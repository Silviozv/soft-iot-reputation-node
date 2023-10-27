package reputation.node.tasks;

import java.util.TimerTask;
import java.util.logging.Logger;
import reputation.node.models.Node;

/**
 * Classe responsável pela tarefa de publicar quais são os serviços providos 
 * pelo nó.
 * 
 * @author Allan Capistrano
 * @version 1.0.0
 */
public class PublishNodeServicesTask extends TimerTask {

  private final Node node;
  private static final Logger logger = Logger.getLogger(
    PublishNodeServicesTask.class.getName()
  );

  public PublishNodeServicesTask(Node node) {
    this.node = node;
  }

  @Override
  public void run() {
    if (this.node.getAmountDevices() > 0) {
      logger.info("Publishing the node services...");

      try {
        this.node.publishNodeServices();
      } catch (InterruptedException ie) {
        logger.warning("Could not sent the transaction.");
        logger.warning(ie.getStackTrace().toString());
      }
    }
  }
}
