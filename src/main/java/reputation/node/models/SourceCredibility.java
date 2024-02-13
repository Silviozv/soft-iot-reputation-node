package reputation.node.models;

/**
 * Classe responsável por relacionar o ID de um nó com sua respectiva
 * credibilidade.
 *
 * @author Allan Capistrano
 * @version 1.0.0
 */
public final class SourceCredibility {

  private final String source;
  private final Float credibility;

  /**
   * Método construtor.
   *
   * @param source String - ID do nó.
   * @param credibility Float - Credibilidade do Nó.
   */
  public SourceCredibility(String source, Float credibility) {
    this.source = source;
    this.credibility = credibility;
  }

  public String getSource() {
    return source;
  }

  public Float getCredibility() {
    return credibility;
  }

  @Override
  public String toString() {
    return String.format(
      "Source: %s | Credibility: %f",
      this.getSource(),
      this.getCredibility()
    );
  }
}
