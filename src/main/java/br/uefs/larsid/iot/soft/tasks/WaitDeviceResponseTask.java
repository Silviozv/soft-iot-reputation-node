package br.uefs.larsid.iot.soft.tasks;

import br.uefs.larsid.iot.soft.models.NodeType;
import java.util.TimerTask;
import java.util.logging.Logger;

/**
 * Classe responsável por verificar se houve resposta do dispositivo à
 * requisição feita pelo nó.
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
   * @param deviceId
   * @param timeoutWaitDeviceResponse
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
      this.node.getNode().evaluateDevice(0);
      this.cancel();
    }
  }
}
