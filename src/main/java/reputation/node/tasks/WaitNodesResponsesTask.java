package reputation.node.tasks;

import java.util.TimerTask;
import java.util.logging.Logger;
import reputation.node.models.Node;

/**
 * Classe responsável por determinar um tempo limite para um nó receber as
 * respostas dos outros nós à solicitação de serviços.
 *
 * @author Allan Capistrano
 * @version 1.0.0
 */
public class WaitNodesResponsesTask extends TimerTask {

  private int timer, timeoutWaitNodesResponses;
  private final Node node;
  private static final Logger logger = Logger.getLogger(
    WaitNodesResponsesTask.class.getName()
  );

  /**
   * Método construtor
   *
   * @param timeoutWaitNodesResponses int - Tempo máximo para aguardar a
   * resposta dos nós.
   * @param node Node - Nó o qual está esperando a resposta.
   */
  public WaitNodesResponsesTask(int timeoutWaitNodesResponses, Node node) {
    this.timeoutWaitNodesResponses = timeoutWaitNodesResponses;
    this.node = node;
    this.timer = 0;
  }

  @Override
  public void run() {
    this.timer++;

    if ((timer * 1000) >= this.timeoutWaitNodesResponses) {
      logger.info("Timeout waiting for Nodes responses.");

      /**
       * Impede o nó de receber as respostas dos outros nós.
       */
      this.node.setCanReceiveNodesResponse(false);

      /**
       * Usando e avaliando o serviço do nó com a maior reputação, e
       * avaliando-o.
       */
      this.node.useNodeService();

      this.cancel();
    }
  }
}
