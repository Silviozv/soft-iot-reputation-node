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
  private final String nodeServiceProviderId;
  private final Node node;
  private static final Logger logger = Logger.getLogger(
    WaitNodeResponseTask.class.getName()
  );

  /**
   * Método construtor.
   *
   * @param nodeServiceProviderId String - ID do nó que enviará a resposta
   * @param timeoutWaitNodeResponse int - Tempo máximo para aguardar a
   * resposta do nó.
   * @param node Node - Nó o qual está esperando a resposta.
   */
  public WaitNodeResponseTask(
    String nodeServiceProviderId,
    int timeoutWaitNodeResponse,
    Node node
  ) {
    this.timeoutWaitNodeResponse = timeoutWaitNodeResponse;
    this.nodeServiceProviderId = nodeServiceProviderId;
    this.node = node;
  }

  @Override
  public void run() {
    logger.info(
      String.format(
        "Waiting for %s response: %d...",
        this.nodeServiceProviderId,
        ++timer
      )
    );

    if ((timer * 1000) >= this.timeoutWaitNodeResponse) {
      logger.warning(
        "Timeout for waiting for " + this.nodeServiceProviderId + " response."
      );

      this.node.setRequestingService(false);

      // Avaliação de serviço prestado incorretamente.
      try {
        this.node.getNodeType()
          .getNode()
          .evaluateServiceProvider(this.nodeServiceProviderId, 0);
      } catch (InterruptedException e) {
        logger.warning("Could not add transaction on tangle network.");
      }
      this.cancel();
    }
  }
}
