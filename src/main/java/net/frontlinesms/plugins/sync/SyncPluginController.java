package net.frontlinesms.plugins.sync;

import java.util.List;

import net.frontlinesms.FrontlineSMS;
import net.frontlinesms.data.domain.FrontlineMessage;
import net.frontlinesms.data.events.EntitySavedNotification;
import net.frontlinesms.events.EventBus;
import net.frontlinesms.events.EventObserver;
import net.frontlinesms.events.FrontlineEventNotification;
import net.frontlinesms.plugins.BasePluginController;
import net.frontlinesms.plugins.PluginControllerProperties;
import net.frontlinesms.plugins.PluginInitialisationException;
import net.frontlinesms.plugins.PluginSettingsController;
import net.frontlinesms.plugins.sync.ui.SyncPluginThinletTabController;
import net.frontlinesms.ui.UiGeneratorController;

import org.springframework.context.ApplicationContext;

@PluginControllerProperties(name = "Sync (beta)",
		iconPath = "/icons/sync_small.png",
		springConfigLocation=PluginControllerProperties.NO_VALUE,
		hibernateConfigPath=PluginControllerProperties.NO_VALUE,
		i18nKey="plugins.sync.name")
public class SyncPluginController extends BasePluginController implements EventObserver {
//> PROPERTIES
	private FrontlineSMS frontlineController;
	private QueueProcessor queueProcessor;
	private SyncMessageDao syncMessageDao;
	private EventBus eventBus;
	private SyncPluginThinletTabController tabController;
	private boolean autoStartup;
	private String syncURL;
	
	QueueProcessor getQueueProcessor() {
		return queueProcessor;
	}
	void setQueueProcessor(QueueProcessor queueProcessor) {
		this.queueProcessor = queueProcessor;
	}
	
	SyncMessageDao getSyncMessageDao() {
		return syncMessageDao;
	}
	
	void setEventBus(EventBus eventBus) {
		this.eventBus = eventBus;
	}
	
	protected Object initThinletTab(UiGeneratorController uiController) {
		this.tabController = new SyncPluginThinletTabController(this, uiController);
		
		// Set the synchronisation URL on the UI
		this.tabController.setSynchronisationURL(this.syncURL);
		
		// Set the start up mode on the UI
		this.tabController.setStartupMode(this.autoStartup);
		
		return this.tabController.getTab();
	}

	public void init(FrontlineSMS frontlineController, ApplicationContext applicationContext) throws PluginInitialisationException {
		this.frontlineController = frontlineController;
		
		// Create the queue processor
		createQueueProcessor(false);
		
		setEventBus(frontlineController.getEventBus());
		this.eventBus.registerObserver(this);
		
	}
	
	private void createQueueProcessor(boolean override) {
		QueueProcessor queueProcessor = new QueueProcessor(this);
		// TODO configure syncher depending on settings
		
		SyncPluginProperties syncProperties  = SyncPluginProperties.getInstance();
		
		// Get the start up mode
		autoStartup = syncProperties.isAutomaticStartup();
		
		// Get the sync URL
		syncURL = syncProperties.getSynchronisationURL();
		
		// Set the synchronisation URL on the UI
		
		// Instantiate the message syncher
		MessageSyncher syncher = new MessageSyncher(syncURL, syncProperties.getParamsMap());
		syncher.setRequestMethod(syncProperties.getRequestMethod());
		
		queueProcessor.setMessageSyncher(syncher);
		setQueueProcessor(queueProcessor);
		
		// Queue unsynchronised messages
		this.syncMessageDao = new SyncMessageDao(frontlineController.getMessageDao());
		queueUnsynchronizedMessages();
		
		// Automatically startup the queue processor only if the startup mode = TRUE
		if (autoStartup || override) {
			this.queueProcessor.start();
		}
		
	}

	public void deinit() {
		// Shutdown message processor and discard
		this.eventBus.unregisterObserver(this);
		stopIfRunning();
	}

	private void queueUnsynchronizedMessages() {
		// TODO Auto-generated method stub
		long lastId = SyncPluginProperties.getInstance().getLastSyncedId();
		
		List<FrontlineMessage> messages = this.syncMessageDao.getUnsynchronisedMessages(lastId);
		
		this.queueProcessor.queue(messages);
	}

	public void notify(FrontlineEventNotification e) {
		if(e instanceof EntitySavedNotification<?>) {
			Object databaseEntity = ((EntitySavedNotification<?>) e).getDatabaseEntity();
			if(databaseEntity instanceof FrontlineMessage) {
				// Get the frontline message instance
				FrontlineMessage m = (FrontlineMessage) databaseEntity;
				
				if (m.getType() == FrontlineMessage.Type.RECEIVED) {
					this.queueProcessor.queue(m);
				}
			}
		}
	}
	
	public synchronized void startIfNotStarted() {
		if(this.queueProcessor == null) {
			// Re-create the queue processor
			createQueueProcessor(true);
		}
	}
	
	public synchronized void stopIfRunning() {
		if(this.queueProcessor != null) {
			// Stop the queue processor
			this.queueProcessor.stopProcessing();

			// Destroy the current queue processor reference
			this.queueProcessor = null;
		}
	}
	
	@Override
	public PluginSettingsController getSettingsController(UiGeneratorController ui) {
		return new SyncPluginSettingsController(ui, getIcon(this.getClass()));
	}
	
	/** Updates the UI with the sync URL and sucess*/
	public void updateSynchronisationLog(String requestURL, boolean success) {
		// Only update the UI after the UI has been initalised 
		if (this.tabController != null) {
			String successText = (success)? "Succeeded!": "Failed!";
			this.tabController.updateSynchronisationLog(requestURL + "..." + successText);
		}
	}
}
