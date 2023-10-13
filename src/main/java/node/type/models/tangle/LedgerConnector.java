package node.type.models.tangle;

import dlt.client.tangle.hornet.model.transactions.IndexTransaction;
import dlt.client.tangle.hornet.model.transactions.Transaction;
import dlt.client.tangle.hornet.services.ILedgerReader;
import dlt.client.tangle.hornet.services.ILedgerSubscriber;
import dlt.client.tangle.hornet.services.ILedgerWriter;

/**
 * 
 * @author Allan Capistrano
 * @version 1.0.0
 */
public class LedgerConnector {

  private ILedgerReader ledgerReader;
  private ILedgerWriter ledgerWriter;

  /**
   * Inscreve em um tópico para escutar as transações que são realizadas.
   *
   * @param topic String - Tópico.
   * @param iLedgerSubscriber ILedgerSubscriber - Objeto para inscrição.
   */
  public void subscribe(String topic, ILedgerSubscriber iLedgerSubscriber) {
    this.ledgerReader.subscribe(topic, iLedgerSubscriber);
  }

  /**
   * Se desinscreve de um tópico.
   *
   * @param topic String - Tópico.
   * @param iLedgerSubscriber ILedgerSubscriber - Objeto para inscrição.
   */
  public void unsubscribe(String topic, ILedgerSubscriber iLedgerSubscriber) {
    this.ledgerReader.unsubscribe(topic, iLedgerSubscriber);
  }

  /**
   * Põe uma transação para ser publicada na Tangle.
   *
   * @param indexTransaction IndexTransaction - Transação que será publicada,
   * juntamente com seu índice.
   * @throws InterruptedException
   */
  public void put(IndexTransaction indexTransaction)
    throws InterruptedException {
    this.ledgerWriter.put(indexTransaction);
  }

  /**
   * Obtém uma transação a partir do ID da mesma.
   *
   * @param id String - ID da transação.
   * @return Transaction.
   */
  public Transaction getTransactionById(String id) {
    return this.ledgerReader.getTransactionById(id);
  }

  public ILedgerWriter getLedgerWriter() {
    return ledgerWriter;
  }

  public void setLedgerWriter(ILedgerWriter ledgerWriter) {
    this.ledgerWriter = ledgerWriter;
  }

  public ILedgerReader getLedgerReader() {
    return ledgerReader;
  }

  public void setLedgerReader(ILedgerReader ledgerReader) {
    this.ledgerReader = ledgerReader;
  }
}
