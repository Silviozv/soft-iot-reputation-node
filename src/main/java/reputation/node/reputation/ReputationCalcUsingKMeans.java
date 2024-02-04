package reputation.node.reputation;

import dlt.client.tangle.hornet.model.transactions.Transaction;
import dlt.client.tangle.hornet.model.transactions.reputation.Evaluation;
import java.util.List;
import java.util.OptionalDouble;
import java.util.stream.Collectors;
import python.to.java.services.IKMeans;
import reputation.node.models.SourceCredibility;
import reputation.node.reputation.credibility.INodeCredibility;
import reputation.node.reputation.credibility.NodeCredibility;

/**
 * Responsável por calcular a reputação de uma coisa, com o uso do algoritmo
 * KMeans.
 *
 * @author Allan Capistrano
 * @version 1.0.0
 */
public class ReputationCalcUsingKMeans implements IReputation {

  private final IKMeans kMeans;
  private final INodeCredibility nodeCredibility;
  private final String sourceId;

  /**
   * Método construtor
   *
   * @param kMeans IKMeans - Referência ao bundle responsável pela execução do
   * algoritmo KMeans.
   * @param nodeCredibility NodeCredibility - Objeto responsável por lidar com
   * a credibilidade dos nós.
   * @param sourceId String - ID do nó avaliador.
   */
  public ReputationCalcUsingKMeans(
    IKMeans kMeans,
    NodeCredibility nodeCredibility,
    String sourceId
  ) {
    this.kMeans = kMeans;
    this.sourceId = sourceId;
    this.nodeCredibility = nodeCredibility;
  }

  /**
   * Calcula a reputação de uma coisa.
   *
   * @param evaluationTransactions List<Transaction> - Lista com as transações
   * de avaliação da coisa.
   * @return Double
   */
  @Override
  public Double calc(List<Transaction> evaluationTransactions) {
    double reputation = 0.0;

    List<SourceCredibility> nodesCredibilityWithSource =
      this.nodeCredibility.getNodesEvaluatorsCredibility(
          evaluationTransactions,
          this.sourceId,
          true
        );

    if (!nodesCredibilityWithSource.isEmpty()) {
      /* Obtendo somente o valor da credibilidade dos nós avaliadores. */
      List<Float> nodesCredibility = nodesCredibilityWithSource
        .stream()
        .map(SourceCredibility::getCredibility)
        .collect(Collectors.toList());

      /* Executando o algoritmo KMeans. */
      List<Float> kMeansResult = this.kMeans.execute(nodesCredibility);

      /* Obtendo somente os nós que possuem as credibilidades calculadas pelo algoritmo KMeans. */
      List<SourceCredibility> nodesWithHighestCredibilities = nodesCredibilityWithSource
        .stream()
        .filter(node -> kMeansResult.contains(node.getCredibility()))
        .collect(Collectors.toList());

      /* Calculando a média das avaliações dos nós calculadas pelo algoritmo KMeans. */
      OptionalDouble temp = evaluationTransactions
        .stream()
        .filter(nodeEvaluation ->
          nodesWithHighestCredibilities
            .stream()
            .anyMatch(sourceCredibility ->
              nodeEvaluation.getSource().equals(sourceCredibility.getSource())
            )
        )
        .mapToDouble(nodeEvaluation -> ((Evaluation) nodeEvaluation).getValue())
        .average();

      /* Caso existam transações de avaliação, atualiza o valor de R como a média dessas avaliações. */
      if (temp.isPresent()) {
        reputation = temp.getAsDouble();
      }
    }

    return reputation;
  }
}
