package reputation.node.tasks;

import java.util.TimerTask;
import java.util.logging.Logger;
import reputation.node.models.Node;

/**
 * Classe responsável por verificar se houve resposta de um nó à requisição de 
 * serviço feita pelo nó.
 *
 * @author Allan Capistrano
 * @version 1.0.0
 */
public class WaitNodeResponseTask extends TimerTask {

  private int timer, timeoutWaitNodeResponse;
  private final String nodeId;
  private final Node node;
  private static final Logger logger = Logger.getLogger(
    WaitNodeResponseTask.class.getName()
  );

  /**
   * Método construtor.
   *
   * @param nodeId String - ID do nó que enviará a resposta
   * @param timeoutWaitNodeResponse int - Tempo máximo para aguardar a
   * resposta do nó.
   * @param node Node - Nó o qual está esperando a resposta.
   */
  public WaitNodeResponseTask(
    String nodeId,
    int timeoutWaitNodeResponse,
    Node node
  ) {
    this.timeoutWaitNodeResponse = timeoutWaitNodeResponse;
    this.nodeId = nodeId;
    this.node = node;
  }

  @Override
  public void run() {
    logger.info(
      String.format("Waiting for %s response: %d...", this.node, ++timer)
    );

    if ((timer * 1000) >= this.timeoutWaitNodeResponse) {
      logger.warning("Timeout for waiting for " + this.nodeId + " response.");

      // Avaliação de serviço prestado incorretamente.
      try {
        this.node.getNodeType().getNode().evaluateDevice(this.nodeId, 0); // TODO: Trocar para evaluateNode
      } catch (InterruptedException e) {
        logger.warning("Could not add transaction on tangle network.");
      }
      this.cancel();
    }
  }
}
