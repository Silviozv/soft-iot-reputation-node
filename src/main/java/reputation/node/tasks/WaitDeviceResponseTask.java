package reputation.node.tasks;

import java.util.TimerTask;
import java.util.logging.Logger;
import reputation.node.models.Node;

/**
 * Classe responsável por verificar se houve resposta do dispositivo à
 * requisição feita pelo nó.
 *
 * @author Allan Capistrano
 * @version 1.1.0
 */
public class WaitDeviceResponseTask extends TimerTask {

  private int timer, timeoutWaitDeviceResponse;
  private final String deviceId;
  private final Node node;
  private static final Logger logger = Logger.getLogger(
    WaitDeviceResponseTask.class.getName()
  );

  /**
   * Método construtor.
   *
   * @param deviceId String - ID do dispositivo.
   * @param timeoutWaitDeviceResponse int - Tempo máximo para aguardar a
   * resposta do dispositivo.
   * @param node Node - Nó o qual está esperando a resposta.
   */
  public WaitDeviceResponseTask(
    String deviceId,
    int timeoutWaitDeviceResponse,
    Node node
  ) {
    this.timer = 0;
    this.timeoutWaitDeviceResponse = timeoutWaitDeviceResponse;
    this.deviceId = deviceId;
    this.node = node;
  }

  @Override
  public void run() {
    logger.info(
      String.format("Waiting for %s response: %d...", this.deviceId, ++timer)
    );

    if ((timer * 1000) >= timeoutWaitDeviceResponse) {
      logger.warning("Timeout for waiting for " + this.deviceId + " response.");

      // Avaliação de serviço prestado incorretamente.
      try {
        this.node.getNodeType()
          .getNode()
          .evaluateServiceProvider(this.deviceId, 0, false);
      } catch (InterruptedException e) {
        logger.warning("Could not add transaction on tangle network.");
      }
      this.cancel();
    }
  }
}
