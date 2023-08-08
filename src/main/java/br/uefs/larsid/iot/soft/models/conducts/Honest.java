package br.uefs.larsid.iot.soft.models.conducts;

import br.uefs.larsid.iot.soft.enums.ConductType;
import java.util.logging.Logger;

public class Honest extends Conduct {

  private static final Logger logger = Logger.getLogger(Honest.class.getName());

  public Honest() {
    this.defineConduct();
  }

  /**
   * Define o comportamento do nó.
   */
  @Override
  public void defineConduct() {
    this.setConductType(ConductType.HONEST);
  }

  /**
   * Avalia o serviço que foi prestado pelo dispositivo, de acordo com o tipo de
   * comportamento do nó.
   *
   * @param value int - Valor da avaliação.
   */
  @Override
  public void evaluateDevice(int value) {
    switch (value) {
      case 0:
        logger.info("Did not provide the service.");
        break;
      case 1:
        logger.info("Provided the service.");
        break;
      default:
        logger.warning("Unable to evaluate the device");
        break;
    }
  }
}
