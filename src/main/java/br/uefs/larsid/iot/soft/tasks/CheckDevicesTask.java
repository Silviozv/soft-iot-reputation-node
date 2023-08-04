package br.uefs.larsid.iot.soft.tasks;

import br.uefs.larsid.iot.soft.models.NodeType;
import java.io.IOException;
import java.util.TimerTask;
import java.util.logging.Logger;

/**
 * Classe responsável pela tarefa de atualizar a lista de dispositivos
 * conectados ao nó.
 */
public class CheckDevicesTask extends TimerTask {

  private final NodeType node;
  private static final Logger logger = Logger.getLogger(
    CheckDevicesTask.class.getName()
  );

  public CheckDevicesTask(NodeType node) {
    this.node = node;
  }

  @Override
  public void run() {
    logger.info("Checking connected devices.");

    try {
      this.node.updateDeviceList();
    } catch (IOException e) {
      logger.severe("Unable to update device list.");
      logger.severe(e.getStackTrace().toString());
      this.cancel();
    }
  }
}
