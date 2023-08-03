package br.uefs.larsid.iot.soft.models.conducts;

import br.uefs.larsid.iot.soft.enums.ConductType;
import java.util.Random;

public class Malicious extends Conduct {

  private final float honestyRate;

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

  public float getHonestyRate() {
    return honestyRate;
  }
}
