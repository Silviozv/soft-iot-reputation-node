package br.uefs.larsid.iot.soft.models;

import br.uefs.larsid.iot.soft.models.conducts.Conduct;
import br.uefs.larsid.iot.soft.models.conducts.Honest;
import br.uefs.larsid.iot.soft.models.conducts.Malicious;
import br.uefs.larsid.iot.soft.services.NodeTypeService;
import br.uefs.larsid.iot.soft.utils.MQTTClient;
import java.util.logging.Logger;

public class NodeType implements NodeTypeService {

  private MQTTClient MQTTClient;
  private int nodeType;
  private float honestyRate;
  private Conduct node;
  private static final Logger logger = Logger.getLogger(
    NodeType.class.getName()
  );

  public NodeType() {}

  public void start() {
    // TODO: Adicioanr os demais tipos de nós.
    switch (nodeType) {
      case 1:
        node = new Honest();
        break;
      case 2:
        node = new Malicious(honestyRate);
        break;
      default:
        logger.severe("Error. No node type for this option.");
        this.stop();
        break;
    }

    this.MQTTClient.connect();

    // Apenas para testes.
    this.node.evaluateDevice();
  }

  public void stop() {
    this.MQTTClient.disconnect();
    // TODO: Desinscrever dos tópicos
  }

  public MQTTClient getMQTTClient() {
    return MQTTClient;
  }

  public void setMQTTClient(MQTTClient mQTTClient) {
    MQTTClient = mQTTClient;
  }

  public int getNodeType() {
    return nodeType;
  }

  public void setNodeType(int nodeType) {
    this.nodeType = nodeType;
  }

  public float getHonestyRate() {
    return honestyRate;
  }

  public void setHonestyRate(float honestyRate) {
    this.honestyRate = honestyRate;
  }

  public Conduct getNode() {
    return node;
  }

  public void setNode(Conduct node) {
    this.node = node;
  }
}
