package reputation.node.tasks;

import java.util.Random;
import java.util.TimerTask;
import java.util.logging.Logger;
import reputation.node.enums.NodeServiceType;
import reputation.node.models.Node;

/**
 * Classe responsável pela tarefa de obter os nós que prestam um determinado
 * serviço
 *
 * @author Allan Capistrano
 * @version 1.0.0
 */
public class CheckNodesServicesTask extends TimerTask {

  private final Node node;
  private static final Logger logger = Logger.getLogger(
    CheckDevicesTask.class.getName()
  );

  /**
   * Método construtor.
   *
   * @param node NodeType - Nó que verificará os serviços dos outros nós.
   */
  public CheckNodesServicesTask(Node node) {
    this.node = node;
  }

  @Override
  public void run() {
    /**
     * Serviço sendo escolhido de maneira aleatório.
     */
    int randomIndex = new Random().nextInt(NodeServiceType.values().length);

    NodeServiceType nodeServiceType = NodeServiceType.values()[randomIndex];

    logger.info(
      "Checking nodes with " + nodeServiceType.getDescription() + " service."
    );

    this.node.getNodesServices(nodeServiceType);
  }
}
