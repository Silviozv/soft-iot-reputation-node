package reputation.node.reputation.credibility;

import dlt.client.tangle.hornet.model.transactions.Transaction;
import dlt.client.tangle.hornet.model.transactions.reputation.Credibility;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import reputation.node.models.SourceCredibility;
import reputation.node.tangle.LedgerConnector;

/**
 * Responsável por lidar com a credibilidade do nó.
 *
 * @author Allan Capistrano
 * @version 1.0.0
 */
public final class NodeCredibility implements INodeCredibility {

  private LedgerConnector ledgerConnector;

  public NodeCredibility() {}

  /**
   * Obtém a credibilidade mais recente de um nó.
   *
   * @param nodeId String - ID do nó que se deseja saber a credibilidade.
   * @return float
   */
  public float get(String nodeId) {
    String index = "cred_" + nodeId;

    List<Transaction> tempCredibility =
      this.ledgerConnector.getLedgerReader()
        .getTransactionsByIndex(index, false);

    float nodeCredibility = Optional
      .ofNullable(tempCredibility)
      .map(transactions ->
        transactions
          .stream()
          .sorted((t1, t2) ->/* Ordenando em ordem decrescente. */
            Long.compare(t2.getCreatedAt(), t1.getCreatedAt())
          )
          .collect(Collectors.toList())
      )
      .filter(list -> !list.isEmpty())
      /* Obtendo a credibilidade mais recente calculada pelo nó */
      .map(list -> ((Credibility) list.get(0)).getValue())
      /* Caso o nó ainda não tenha calculado a sua credibilidade, por padrão é 0.5. */
      .orElse((float) 0.5);

    return nodeCredibility;
  }

  /**
   * Obtém e adiciona em uma lista, os valores da credibilidade mais recente dos
   * nós avaliadores cujos IDs foram informados pela lista de transações de
   * avaliações.
   *
   * @param serviceProviderEvaluationTransactions List<Transaction> - Lista de
   * transações de avaliações.
   * @param sourceId String - ID do atual nó avaliador.
   * @param useOwnEvaluations boolean - Indica se deve ou não considerar as próprias
   * avaliações.
   * @return List<SourceCredibility>
   */
  public List<SourceCredibility> getNodesEvaluatorsCredibility(
    List<Transaction> serviceProviderEvaluationTransactions,
    String sourceId,
    boolean useOwnEvaluations
  ) {
    List<SourceCredibility> nodesCredibility = new ArrayList<>();

    if (
      Optional.ofNullable(serviceProviderEvaluationTransactions).isPresent()
    ) {
      List<Transaction> uniqueServiceProviderEvaluationTransactions;

      if (useOwnEvaluations) {
        /* Filtrando somente uma avaliação por nó avaliador. */
        uniqueServiceProviderEvaluationTransactions =
          serviceProviderEvaluationTransactions
            .stream()
            .collect(
              Collectors.toMap(
                Transaction::getSource,
                obj -> obj,
                (existing, replacement) -> existing
              )
            )
            .values()
            .stream()
            .collect(Collectors.toList());
      } else {
        /* Filtrando somente uma avaliação por nó avaliador, e não levando em 
      consideração as avaliações do atual nó avaliador. */
        uniqueServiceProviderEvaluationTransactions =
          serviceProviderEvaluationTransactions
            .stream()
            .collect(
              Collectors.toMap(
                Transaction::getSource,
                obj -> obj,
                (existing, replacement) -> existing
              )
            )
            .values()
            .stream()
            .filter(transaction -> !transaction.getSource().equals(sourceId))
            .collect(Collectors.toList());
      }

      for (Transaction transaction : uniqueServiceProviderEvaluationTransactions) {
        /* O index para pegar a credibilidade segue o formato: cred_<id do nó>. */
        String source = transaction.getSource();

        float nodeCredibility = this.get(source);

        nodesCredibility.add(new SourceCredibility(source, nodeCredibility));
      }
    }

    return nodesCredibility;
  }

  public LedgerConnector getLedgerConnector() {
    return ledgerConnector;
  }

  public void setLedgerConnector(LedgerConnector ledgerConnector) {
    this.ledgerConnector = ledgerConnector;
  }
}
