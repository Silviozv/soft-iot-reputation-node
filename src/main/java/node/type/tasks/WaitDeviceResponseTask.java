package node.type.tasks;

import java.util.TimerTask;
import java.util.logging.Logger;
import node.type.models.NodeType;

/**
 * Classe responsável por verificar se houve resposta do dispositivo à
 * requisição feita pelo nó.
 *
 * @author Allan Capistrano
 * @version 1.0.0
 */
public class WaitDeviceResponseTask extends TimerTask {

  private int timer, timeoutWaitDeviceResponse;
  private final String deviceId;
  private final NodeType node;
  private static final Logger logger = Logger.getLogger(
    WaitDeviceResponseTask.class.getName()
  );

  /**
   * Método construtor.
   *
   * @param deviceId String - ID do dispositivo.
   * @param timeoutWaitDeviceResponse int - Tempo máximo para aguardar a
   * resposta do dispositivo.
   */
  public WaitDeviceResponseTask(
    String deviceId,
    int timeoutWaitDeviceResponse,
    NodeType node
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
        this.node.getNode().evaluateDevice(this.deviceId, 0);
      } catch (InterruptedException e) {
        logger.warning("Could not add transaction on tangle network.");
      }
      this.cancel();
    }
  }
}
