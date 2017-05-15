package org.tgi.action;

import org.adempiere.webui.action.IAction;
import org.adempiere.webui.adwindow.ADWindow;
import org.adempiere.webui.adwindow.AbstractADWindowContent;
import org.adempiere.webui.apps.AEnv;
import org.adempiere.webui.component.ConfirmPanel;
import org.adempiere.webui.component.Label;
import org.adempiere.webui.component.Window;
import org.adempiere.webui.editor.WTableDirEditor;
import org.adempiere.webui.window.FDialog;
import org.compiere.model.GridTab;
import org.compiere.model.MLookup;
import org.compiere.model.MLookupFactory;
import org.compiere.model.MPInstance;
import org.compiere.model.MPInstancePara;
import org.compiere.model.MPeriod;
import org.compiere.model.MProcess;
import org.compiere.model.MRole;
import org.compiere.process.ProcessInfo;
import org.compiere.util.AdempiereUserError;
import org.compiere.util.DisplayType;
import org.compiere.util.Env;
import org.compiere.util.Msg;
import org.compiere.util.Trx;
import org.zkoss.zk.ui.event.Event;
import org.zkoss.zk.ui.event.EventListener;
import org.zkoss.zk.ui.event.Events;
import org.zkoss.zul.Hbox;
import org.zkoss.zul.Vbox;

public class PeriodOpenClose implements IAction {

	final int PROCESS_PERIOD_ID = 167;
	final int REFERENCE_PERIODCONTROL_ACTION = 176;

	public void execute(Object target) {

		MRole role = MRole.getDefault();
		Boolean access = role.getProcessAccess(PROCESS_PERIOD_ID);
		if (access == null || !access.booleanValue())
			throw new IllegalAccessError(Msg.getMsg(Env.getCtx(), "CannotAccessProcess", new Object[] {PROCESS_PERIOD_ID, role.getName()}));

		final AbstractADWindowContent window = ((ADWindow) target).getADWindowContent();
		final GridTab gridTab = getGridTab(window);

		if (gridTab == null)
			FDialog.info(window.getWindowNo(), null, "You are not on the correct tab");
		else {
			ActionWindow aw = new ActionWindow();
			aw.init(window, gridTab);
			AEnv.showWindow(aw);	
		}
	}

	GridTab getGridTab(AbstractADWindowContent window) {
		if (window.getADTab().getSelectedDetailADTabpanel().getTableName().equals("C_Period"))
			return window.getADTab().getSelectedDetailADTabpanel().getGridTab();
		else if (window.getActiveGridTab().getTableName().equals("C_Period"))
			return window.getActiveGridTab();
		return null;
	}

	public class ActionWindow extends Window implements EventListener<Event> {

		private static final long serialVersionUID = -6975855302526207150L;
		private ConfirmPanel confirmPanel = new ConfirmPanel(true);
		WTableDirEditor fAction;
		GridTab gridTab;
		AbstractADWindowContent window;
		public ActionWindow() {
		}

		public void init(AbstractADWindowContent window, GridTab gridTab) {
			this.window = window;
			this.gridTab = gridTab;

			try {
				MLookup lookup =  MLookupFactory.get (Env.getCtx(), window.getWindowNo(), 0, DisplayType.List, Env.getLanguage(Env.getCtx()), "", REFERENCE_PERIODCONTROL_ACTION, false, "Value IN ('C', 'O')");
				fAction = new WTableDirEditor("Action", true, false, true, lookup);
				fAction.getComponent().addEventListener(Events.ON_SELECT, this);
			}
			catch(Exception e) {

			}

			setTitle("Open/Close selected periods");
			setWidth("450px");
			setClosable(true);
			setSizable(true);
			setBorder("normal");
			setStyle("position:absolute");

			Label lAction = new Label("Action");

			confirmPanel = new ConfirmPanel(true);
			confirmPanel.addActionListener(this);

			Vbox vb = new Vbox();
			vb.setWidth("100%");
			appendChild(vb);

			Hbox hb = new Hbox();
			hb.appendChild(lAction);
			hb.appendChild(fAction.getComponent());

			vb.appendChild(hb);
			vb.appendChild(confirmPanel);
			setAttribute(Window.MODE_KEY, Window.MODE_HIGHLIGHTED);
		}

		public void onEvent(Event event) throws Exception {
			if (event.getTarget().equals(confirmPanel.getButton("Ok"))) {

				int size = gridTab.getRowCount();
				int cnt= 0;
				for (int i = 0; i < size; i++) {
					if (gridTab.isSelected(i)) {
						MProcess process = MProcess.get(Env.getCtx(), PROCESS_PERIOD_ID);

						MPInstance instance = new MPInstance(Env.getCtx(), process.getAD_Process_ID(), gridTab.getKeyID(i));
						instance.saveEx();

						MPInstancePara ip = new MPInstancePara(instance, 1);
						ip.setParameterName("PeriodAction");
						ip.setP_String((String) fAction.getValue());
						ip.saveEx();

						ProcessInfo pi = new ProcessInfo ("PeriodOpenClose", process.getAD_Process_ID(), MPeriod.Table_ID, gridTab.getKeyID(i));
						pi.setAD_User_ID(Env.getAD_User_ID(Env.getCtx()));
						pi.setAD_Client_ID(Env.getAD_Client_ID(Env.getCtx()));
						pi.setAD_PInstance_ID(instance.getAD_PInstance_ID());		

						String trxName = Trx.createTrxName("PeriodOpenClose" + gridTab.getKeyID(i));
						Trx trx = Trx.get(trxName, true);
						if (!process.processIt(pi, trx)) 
							throw new AdempiereUserError("Process failed: (" + pi.getClassName() + ") " + pi.getSummary());
						else
							cnt++;
					}
				}

				window.getStatusBar().setStatusLine("Mise a jour " + " : " + cnt, false);
				gridTab.dataRefresh();

				onClose();

			} else if (event.getTarget().equals(confirmPanel.getButton("Cancel"))) {
				onClose();
			}
		}
	}
}
