package br.uefs.larsid.iot.soft.models.conducts;
// TODO: Corrigir package em todas as classes.

import br.uefs.larsid.iot.soft.enums.ConductType;
import br.uefs.larsid.iot.soft.models.tangle.LedgerConnector;
import dlt.client.tangle.hornet.enums.TransactionType;
import dlt.client.tangle.hornet.model.transactions.Evaluation;
import dlt.client.tangle.hornet.model.transactions.Transaction;
import java.util.logging.Logger;

public class Honest extends Conduct {

  private static final Logger logger = Logger.getLogger(Honest.class.getName());

  /**
   * Método construtor.
   *
   * @param ledgerConnector LedgerConnector - Conector para comunicação com a Tangle.
   * @param id String - Identificador único do nó.
   */
  public Honest(LedgerConnector ledgerConnector, String id) {
    super(ledgerConnector, id);
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
   * @param deviceId String - Id do dispositivo que será avaliado.
   * @param value int - Valor da avaliação.
   * @throws InterruptedException
   */
  @Override
  public void evaluateDevice(String deviceId, int value)
    throws InterruptedException {
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

    Transaction transactionEvaluation = new Evaluation(
      this.getId(),
      deviceId,
      TransactionType.REP_EVALUATION,
      value
    );

    // Adicionando avaliação na Tangle.
    this.getLedgerConnector().put(transactionEvaluation); // TODO: Ver o motivo de não está escrevendo corretamente a transação
  }
}
