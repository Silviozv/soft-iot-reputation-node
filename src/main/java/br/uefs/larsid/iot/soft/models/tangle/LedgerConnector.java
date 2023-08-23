package br.uefs.larsid.iot.soft.models.tangle;

import dlt.client.tangle.model.transactions.Transaction;
import dlt.client.tangle.services.ILedgerReader;
import dlt.client.tangle.services.ILedgerSubscriber;
import dlt.client.tangle.services.ILedgerWriter;

/**
 * @author Allan Capistrano
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
   * @param transaction Transaction - Transação que será publicada.
   * @throws InterruptedException
   */
  public void put(Transaction transaction) throws InterruptedException {
    this.ledgerWriter.put(transaction);
  }

  /**
   * Obtém uma transação a partir do hash da mesma.
   * 
   * @param hash String - Hash da transação.
   * @return Transaction.
   */
  public Transaction getTransactionByHash(String hash) {
    return this.ledgerWriter.getTransactionByHash(hash);
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
