package br.uefs.larsid.iot.soft.tasks;

import java.util.TimerTask;
import java.util.logging.Logger;

/**
 * Classe responsável por verificar se houve resposta do dispositivo à
 * requisição feita pelo nó.
 */
public class WaitDeviceResponseTask extends TimerTask {

  private int timer, timeoutWaitDeviceResponse;
  private final String deviceId;
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
    int timeoutWaitDeviceResponse
  ) {
    this.timer = 0;
    this.timeoutWaitDeviceResponse = timeoutWaitDeviceResponse;
    this.deviceId = deviceId;
  }

  @Override
  public void run() {
    logger.info("Waiting for " + this.deviceId + " response.");

    timer++;
    logger.info(String.valueOf(timer));

    if ((timer * 1000) >= timeoutWaitDeviceResponse) {
      this.cancel();
      logger.warning("Timeout for waiting for " + this.deviceId + " response.");
      //TODO: Avaliar o dispositivo como zero.
    }
  }
}
