package br.uefs.larsid.iot.soft.models.conducts;

import br.uefs.larsid.iot.soft.enums.ConductType;
import br.uefs.larsid.iot.soft.models.tangle.LedgerConnector;

public abstract class Conduct {

  private ConductType conductType;
  private final LedgerConnector ledgerConnector;
  private final String id;

  // TODO: Adicionar comentário
  public Conduct(LedgerConnector ledgerConnector, String id) {
    this.ledgerConnector = ledgerConnector;
    this.id = id;
  }

  /**
   * Define o comportamento do nó.
   */
  public abstract void defineConduct();

  /**
   * Avalia o serviço que foi prestado pelo dispositivo, de acordo com o tipo de
   * comportamento do nó.
   *
   * @param deviceId String - Id do dispositivo que será avaliado.
   * @param value int - Valor da avaliação.
   */
  public abstract void evaluateDevice(String deviceId, int value)
    throws InterruptedException;

  public ConductType getConductType() {
    return conductType;
  }

  public void setConductType(ConductType conductType) {
    this.conductType = conductType;
  }

  public LedgerConnector getLedgerConnector() {
    return ledgerConnector;
  }

  public String getId() {
    return id;
  }
}
