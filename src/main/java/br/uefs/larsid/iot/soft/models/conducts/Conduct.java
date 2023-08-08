package br.uefs.larsid.iot.soft.models.conducts;

import br.uefs.larsid.iot.soft.enums.ConductType;

public abstract class Conduct {

  private ConductType conductType;

  public Conduct() {}

  /**
   * Define o comportamento do nó.
   */
  public abstract void defineConduct();

  /**
   * Avalia o serviço que foi prestado pelo dispositivo, de acordo com o tipo de
   * comportamento do nó.
   *
   * @param value int - Valor da avaliação.
   */
  public abstract void evaluateDevice(int value);

  public ConductType getConductType() {
    return conductType;
  }

  public void setConductType(ConductType conductType) {
    this.conductType = conductType;
  }
}
