
package com.jfixby.red.triplane.resources.fsbased;

import com.jfixby.rana.api.pkg.DeployRemoteBanksTask;
import com.jfixby.rana.api.pkg.ResourceRebuildIndexListener;
import com.jfixby.scarabei.api.collections.List;
import com.jfixby.scarabei.api.err.Err;
import com.jfixby.scarabei.api.file.File;
import com.jfixby.scarabei.api.taskman.Job;
import com.jfixby.scarabei.api.taskman.SimpleProgress;
import com.jfixby.scarabei.api.taskman.TaskManager;
import com.jfixby.scarabei.api.taskman.TaskProgress;

public class RedDeployRemoteBanksTask implements DeployRemoteBanksTask, Job, ResourceRebuildIndexListener {
	SimpleProgress progress;
	private final List<RemoteBankSettings> toDeploy;
	private final RedResourcesManager redResourcesManager;

	public RedDeployRemoteBanksTask (final RedResourcesManager redResourcesManager) {
		this.redResourcesManager = redResourcesManager;
		this.progress = TaskManager.newSimpleProgress();
		this.toDeploy = redResourcesManager.getRemoteBanksToDeploy();// unsafe thread action
		this.progress.setTotal(this.toDeploy.size());
	}

	long processed = 0;

	@Override
	public TaskProgress getProgress () {
		return this.progress;
	}

	@Override
	public Job getJob () {
		return this;
	}

	@Override
	public void doStart () throws Throwable {
		this.progress.updateProcessed(this.processed);
	}

	@Override
	public void doPush () throws Throwable {
		if (this.toDeploy.size() == 0) {
			return;
		}
		final RemoteBankSettings e = this.toDeploy.getElementAt(0);
		final File cache = this.redResourcesManager.getAssetsCache();
		this.redResourcesManager.loadRemoteBank(e.bankURL, e.tanks, cache, this);
		this.toDeploy.removeElementAt(0);
		this.processed++;
		this.progress.updateProcessed(this.processed);
	}

	@Override
	public boolean isDone () {
		if (this.toDeploy.size() == 0) {
			this.progress.setIsDone();
			return true;
		}
		return false;
	}

	@Override
	public void onError (final Throwable e) {
// L.e(e);
		Err.reportError(e);
	}

}
