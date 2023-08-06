package br.uefs.larsid.iot.soft.tasks;

import br.uefs.larsid.iot.soft.models.NodeType;
import java.util.TimerTask;
import java.util.logging.Logger;

/**
 * Classe responsável pela tarefa de requisitar dados de um dos sensores de um
 * dispositivo aleatório que estão conectado ao nó.
 */
public class RequestDataTask extends TimerTask {

  private final NodeType node;
  private static final Logger logger = Logger.getLogger(
    RequestDataTask.class.getName()
  );

  public RequestDataTask(NodeType node) {
    this.node = node;
  }

  @Override
  public void run() {
    if (this.node.getAmountDevices() > 0) {
      logger.info("Requesting data from device");

      this.node.requestDataFromRandomDevice();
    }
  }
}
