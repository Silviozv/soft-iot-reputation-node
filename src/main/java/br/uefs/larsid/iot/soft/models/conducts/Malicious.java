package br.uefs.larsid.iot.soft.models.conducts;

import br.uefs.larsid.iot.soft.enums.ConductType;
import br.uefs.larsid.iot.soft.models.tangle.LedgerConnector;
import dlt.client.tangle.hornet.enums.TransactionType;
import dlt.client.tangle.hornet.model.transactions.Evaluation;
import dlt.client.tangle.hornet.model.transactions.Transaction;
import java.util.Random;
import java.util.logging.Logger;

public class Malicious extends Conduct {

  private final float honestyRate;
  private static final Logger logger = Logger.getLogger(
    Malicious.class.getName()
  );

  /**
   * Método construtor.
   *
   * @param ledgerConnector LedgerConnector - Conector para comunicação com a Tangle.
   * @param id String - Identificador único do nó.
   * @param honestyRate float - Taxa de honestidade do nó malicioso.
   */
  public Malicious(
    LedgerConnector ledgerConnector,
    String id,
    float honestyRate
  ) {
    super(ledgerConnector, id);
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
   * @param deviceId String - Id do dispositivo que será avaliado.
   * @param value int - Valor da avaliação. Se o tipo de conduta for 'MALICIOUS'
   * este parâmetro é ignorado.
   * @throws InterruptedException
   */
  @Override
  public void evaluateDevice(String deviceId, int value)
    throws InterruptedException {
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
        value = 0;
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

    Transaction transactionEvaluation = new Evaluation(
      this.getId(),
      deviceId,
      TransactionType.REP_EVALUATION,
      value
    );

    // Adicionando avaliação na Tangle.
    this.getLedgerConnector().put(transactionEvaluation); // TODO: Ver o motivo de não está escrevendo corretamente a transação
  }

  public float getHonestyRate() {
    return honestyRate;
  }
}
