
package com.jfixby.red.triplane.resources.fsbased;

import java.io.IOException;

import com.jfixby.rana.api.asset.AssetsManager;
import com.jfixby.rana.api.asset.SealedAssetsContainer;
import com.jfixby.rana.api.pkg.PACKAGE_STATUS;
import com.jfixby.rana.api.pkg.PackageHandler;
import com.jfixby.rana.api.pkg.PackageReader;
import com.jfixby.rana.api.pkg.PackageReaderListener;
import com.jfixby.rana.api.pkg.PackageVersion;
import com.jfixby.rana.api.pkg.fs.PackageDescriptor;
import com.jfixby.scarabei.api.assets.ID;
import com.jfixby.scarabei.api.collections.Collection;
import com.jfixby.scarabei.api.collections.Collections;
import com.jfixby.scarabei.api.collections.List;
import com.jfixby.scarabei.api.debug.Debug;
import com.jfixby.scarabei.api.err.Err;
import com.jfixby.scarabei.api.file.File;
import com.jfixby.scarabei.api.file.FileConflistResolver;
import com.jfixby.scarabei.api.file.FileSystem;
import com.jfixby.scarabei.api.file.FileSystemSandBox;
import com.jfixby.scarabei.api.log.L;
import com.jfixby.scarabei.api.sys.settings.SystemSettings;
import com.jfixby.scarabei.api.util.JUtils;
import com.jfixby.scarabei.api.util.StateSwitcher;

public class RedPackageHandler implements PackageHandler, PackageVersion {

	final List<ID> descriptors = Collections.newList();
	final List<ID> dependencies = Collections.newList();

	private String version;
	private long timestamp;
	private String root_file_name;

	private final String name;
// private File root_file;
	private PackageFormatImpl format;
	private final ResourceIndex resourceIndex;
	private final File package_folder;
	private final StateSwitcher<PACKAGE_STATUS> status;
	private final File package_cache;

	public RedPackageHandler (final File package_folder, final ResourceIndex resourceIndex) throws IOException {
		this(package_folder, resourceIndex, null);
	}

	public RedPackageHandler (final File package_folder, final ResourceIndex resourceIndex, final File package_cache)
		throws IOException {
		this.resourceIndex = resourceIndex;
		this.package_folder = package_folder;
		this.status = JUtils.newStateSwitcher(PACKAGE_STATUS.NOT_INSTALLED);
		if (package_cache == null) {
			this.status.switchState(PACKAGE_STATUS.INSTALLED);
		}
		final File content_folder = package_folder.child(PackageDescriptor.PACKAGE_CONTENT_FOLDER);
		if (!content_folder.exists()) {
			this.status.switchState(PACKAGE_STATUS.BROKEN);
		}
		this.package_cache = package_cache;
		this.name = package_folder.getName();
	}

	public File getPackageFolder () {
		return this.package_folder;
	}

	@Override
	public PackageVersion getVersion () {
		return this;
	}

	@Override
	public PackageFormatImpl getFormat () {
		return this.format;
	}

	@Override
	public PACKAGE_STATUS getStatus () {
		return this.status.currentState();
	}

	@Override
	public Collection<ID> listPackedAssets () {
		return this.descriptors;
	}

	@Override
	public String toString () {
		return "PackageHandler[" + this.format + "] ver." + this.version + " " + this.descriptors + " timestamp=" + this.timestamp;
	}

	@Override
	public void print () {
		L.d(this);
	}

	@Override
	public void install () {
		this.status.expectState(PACKAGE_STATUS.NOT_INSTALLED);
		final FileSystem fs = this.package_folder.getFileSystem();
		try {
			fs.copyFolderContentsToFolder(this.package_folder, this.package_cache, FileConflistResolver.OVERWRITE_IF_NEW);
			this.status.switchState(PACKAGE_STATUS.INSTALLED);
		} catch (final IOException e) {
			this.status.switchState(PACKAGE_STATUS.BROKEN);
			Err.reportError(e);
		}

	}

	@Override
	public SealedAssetsContainer doReadPackage (final PackageReaderListener reader_listener, final PackageReader reader) {
		this.status.expectState(PACKAGE_STATUS.INSTALLED);

		File read_folder = null;
		if (this.package_cache == null) {
			read_folder = this.package_folder.child(PackageDescriptor.PACKAGE_CONTENT_FOLDER);
		} else {
			read_folder = this.package_cache.child(PackageDescriptor.PACKAGE_CONTENT_FOLDER);
		}

		FileSystem FS = read_folder.getFileSystem();

		File sandbox_folder = null;
		final boolean use_sandbox = SystemSettings.getFlag(AssetsManager.UseAssetSandBox);
		if (FS.isReadOnlyFileSystem() || !use_sandbox) {
			sandbox_folder = read_folder;
		} else {
			try {
				sandbox_folder = FileSystemSandBox.wrap(this.name, read_folder).ROOT();
			} catch (final IOException e) {
				this.status.switchState(PACKAGE_STATUS.BROKEN);
				reader_listener.onError(e);
				return null;
			}
			FS = sandbox_folder.getFileSystem();
		}

		final File root_file = sandbox_folder.child(this.root_file_name);

		try {
			final RedSealedContainer packageData = new RedSealedContainer(this, reader_listener, reader);
			final PackageInputImpl input = new PackageInputImpl(reader_listener, root_file, packageData, this);
			L.d("reading", root_file);
			reader.doReadPackage(input);
			packageData.seal();
			this.isLoaded = true;
			return packageData;
		} catch (final IOException e) {
			this.status.switchState(PACKAGE_STATUS.BROKEN);
			reader_listener.onError(e);
			return null;
		}

	}

	public void flagUnload () {
		this.isLoaded = false;
	}

	boolean isLoaded = false;

	@Override
	public boolean isLoaded () {
		return this.isLoaded;
	}

	public void setFormat (final String format_string) {
		Debug.checkNull("format", format_string);
		Debug.checkEmpty("format", format_string);
		this.format = new PackageFormatImpl(format_string);
	}

	public void setVersion (final String version) {
		Debug.checkNull("version", version);
		Debug.checkEmpty("version", version);
		this.version = version;
	}

	@Override
	public long getTimeStamp () {
		return this.timestamp;
	}

	@Override
	public String getVersionName () {
		return this.version;
	}

	public void setTimestamp (final long timestamp) {
		this.timestamp = timestamp;
	}

	public void setRootFileName (final String root_file_name) {
		this.root_file_name = root_file_name;
	}

	@Override
	public String getPackageName () {
		return this.name;
	}

	@Override
	public Collection<ID> listDependencies () {
		return this.dependencies;
	}

	@Override
	public long reReadTimeStamp () {
		return this.resourceIndex.reReadTimeStamp(this);
	}

}
