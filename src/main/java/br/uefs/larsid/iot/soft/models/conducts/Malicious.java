package br.uefs.larsid.iot.soft.models.conducts;

import br.uefs.larsid.iot.soft.enums.ConductType;
import java.util.Random;
import java.util.logging.Logger;

public class Malicious extends Conduct {

  private final float honestyRate;
  private static final Logger logger = Logger.getLogger(
    Malicious.class.getName()
  );

  public Malicious(float honestyRate) {
    this.honestyRate = honestyRate;
    this.defineConduct();
  }

  /**
   * Define se o comportamento do nó malicioso será honesto ou desonesto.
   */
  @Override
  public void defineConduct() {
    // Gerando um número aleatório entre 0 e 100.
    float randomNumber = new Random().nextFloat() * 100;

    if (randomNumber > honestyRate) {
      this.setConductType(ConductType.MALICIOUS);
    } else {
      this.setConductType(ConductType.HONEST);
    }
  }

  /**
   * Avalia o serviço que foi prestado pelo dispositivo, de acordo com o tipo de
   * comportamento do nó.
   *
   * @param value int - Valor da avaliação. Se o tipo de conduta for 'MALICIOUS'
   * este parâmetro é ignorado.
   */
  @Override
  public void evaluateDevice(int value) {
    switch (this.getConductType()) {
      case HONEST:
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
        break;
      case MALICIOUS:
        logger.info("Did not provide the service.");
        break;
      case SELFISH:
        logger.info("TODO");
        break;
      case CORRUPT:
        logger.info("TODO");
        break;
      case DISTURBING:
        logger.info("TODO");
        break;
      default:
        logger.severe("Error! ConductType not found.");
        break;
    }
  }

  public float getHonestyRate() {
    return honestyRate;
  }
}
