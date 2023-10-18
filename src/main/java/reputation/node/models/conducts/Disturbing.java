package reputation.node.models.conducts;

import dlt.client.tangle.hornet.enums.TransactionType;
import dlt.client.tangle.hornet.model.transactions.Evaluation;
import dlt.client.tangle.hornet.model.transactions.IndexTransaction;
import dlt.client.tangle.hornet.model.transactions.Transaction;
import java.util.logging.Logger;

import reputation.node.enums.ConductType;
import reputation.node.models.tangle.LedgerConnector;

/**
 * Nó do tipo perturbador.
 * 
 * @author Allan Capistrano
 * @version 1.0.0
 */
public class Disturbing extends Conduct {

  private static final Logger logger = Logger.getLogger(
    Disturbing.class.getName()
  );

  /**
   * Método construtor.
   *
   * @param ledgerConnector LedgerConnector - Conector para comunicação com a 
   * Tangle.
   * @param id String - Identificador único do nó.
   */
  public Disturbing(LedgerConnector ledgerConnector, String id) {
    super(ledgerConnector, id);
    this.setConductType(ConductType.HONEST);
  }

  /**
   * Define o comportamento do nó perturbador.
   */
  @Override
  public void defineConduct() {
    this.changeConduct();
  }

  /**
   * Altera a conduta do nó:
   * Se ele for honesto -> muda para malicioso;
   * Se ele for malicioso -> muda para honesto.
   */
  private void changeConduct() {
    this.setConductType(
        this.getConductType() == ConductType.HONEST
          ? ConductType.MALICIOUS
          : ConductType.HONEST
      );

    logger.info(
      "Changing Disturbing node behavior to: " +
      this.getConductType().toString()
    );
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
        value = 0; // Alterando o valor da avaliação para 'serviço não prestado'.
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
    this.getLedgerConnector()
      .put(new IndexTransaction(deviceId, transactionEvaluation));
  }
}
