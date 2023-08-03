package br.uefs.larsid.iot.soft.models.conducts;

import br.uefs.larsid.iot.soft.enums.ConductType;
import java.util.Random;

public class Malicious extends Conduct {

  private float honestyRate;

  public Malicious() {}

  /**
   * Indica se o comportamento do nó malicioso será honesto ou desonesto.
   */
  public void defineConduct() {
    // Gerando um número aleatório entre 0 e 100.
    float randomNumber = new Random().nextFloat() * 100;

    if (randomNumber > honestyRate) {
      this.setConductType(ConductType.MALICIOUS);
    } else {
      this.setConductType(ConductType.HONEST);
    }
  }
}
