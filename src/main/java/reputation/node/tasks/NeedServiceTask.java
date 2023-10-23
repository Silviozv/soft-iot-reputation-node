package reputation.node.tasks;

import java.util.TimerTask;
import java.util.logging.Logger;
import reputation.node.models.Node;

/**
 * Classe responsável pela tarefa de requisitar serviços para os demais nós.
 *
 * @author Allan Capistrano
 * @version 1.0.0
 */
public class NeedServiceTask extends TimerTask {

  private final Node node;
  private static final Logger logger = Logger.getLogger(
    NeedServiceTask.class.getName()
  );

  /**
   * Método construtor
   *
   * @param node NodeType - Nó que realizará a requisição.
   */
  public NeedServiceTask(Node node) {
    this.node = node;
  }

  @Override
  public void run() {
    logger.info("Requesting service from node");

    try {
      this.node.requestServiceFromNode();
    } catch (InterruptedException e) {
      logger.warning("Could not request a service from a node.");
    }
  }
}
