package node.type.tasks;

import java.io.IOException;
import java.util.TimerTask;
import java.util.logging.Logger;
import node.type.models.NodeType;

/**
 * Classe responsável pela tarefa de atualizar a lista de dispositivos
 * conectados ao nó.
 *
 * @author Allan Capistrano
 * @version 1.0.0
 */
public class CheckDevicesTask extends TimerTask {

  private final NodeType node;
  private static final Logger logger = Logger.getLogger(
    CheckDevicesTask.class.getName()
  );

  /**
   * Método construtor.
   *
   * @param node NodeType - Nó que verificará os dispositivos que estão
   * conectados.
   */
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
