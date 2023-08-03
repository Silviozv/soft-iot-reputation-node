package br.uefs.larsid.iot.soft.models.conducts;

import br.uefs.larsid.iot.soft.enums.ConductType;

public class Honest extends Conduct {

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
}
