package br.uefs.larsid.iot.soft.models;

import br.uefs.larsid.iot.soft.services.NodeTypeService;
import java.util.logging.Logger;

public class NodeType implements NodeTypeService {

  private static final Logger logger = Logger.getLogger(
    NodeType.class.getName()
  );

  public NodeType() {}

  public void start() {
    logger.info("Iniciando o bundle");
  }

  public void stop() {
    logger.info("Finalizando o bundle");
  }
}
