package node.type.tasks;

import java.util.TimerTask;
import java.util.logging.Logger;
import node.type.models.NodeType;

/**
 * Classe responsável pela tarefa de requisitar dados de um dos sensores de um
 * dispositivo aleatório que estão conectado ao nó.
 *
 * @author Allan Capistrano
 * @version 1.0.0
 */
public class RequestDataTask extends TimerTask {

  private final NodeType node;
  private static final Logger logger = Logger.getLogger(
    RequestDataTask.class.getName()
  );

  /**
   * Método construtor
   *
   * @param node NodeType - Nó que realizará a requisição.
   */
  public RequestDataTask(NodeType node) {
    this.node = node;
  }

  @Override
  public void run() {
    if (this.node.getAmountDevices() > 0) {
      logger.info("Requesting data from device");

      this.node.requestDataFromRandomDevice();
    }
  }
}
