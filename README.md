# SOFT-IoT-Reputation-Node

O `soft-iot-reputation-node` é o *bundle* responsável pelo gerenciamento dos nós para o serviço de reputação. Ele faz uso dos serviços dos *bundles* `SOFT-IoT-DLT-Client-Tangle-Hornet`, `SOFT-IoT-Node-Type`, `SOFT-IoT-Python-to-Java`, e `SOFT-IoT-Write-CSV` para permitir o funcionamento integrado do sistema como um todo.

## Configurações

| Propriedade | Descrição | Valor Padrão |
| ----------- | --------- | ------------ |
| ip | Endereço IP de onde o *bundle* está sendo executado. | localhost |
| port | 	Porta para conexão com o *broker*. | 1883 |
| user | Usuário para conexão com o *broker*. | karaf |
| pass | Senha para conexão com o *broker*. | karaf |
| checkDeviceTaskTime | Tempo (segundos) para verificação dos dispositivos que estão conectados ao nó. | 5 |
| requestDataTaskTime | Tempo (segundos) para o nó requisitar dados para um dos dispositivos conectados.| 30 |
| waitDeviceResponseTaskTime | Tempo máximo (segundos) de espera da resposta do dispositivo para a requisição feita pelo nó. | 10 |
| checkNodesServicesTaskTime | Tempo (segundos) para vericar quais nós tem um determinado serviço. | 45 |
| waitNodesResponsesTaskTime | Tempo máximo (segundos) de espera da resposta do nós para a requisição de pretação de serviço. | 30 |
| changeDisturbingNodeBehaviorTaskTime | Tempo (segundos) para o nó do tipo Perturbador verificar a própria reputação para alterar o seu comportamento. | 30 |
| useCredibility | Determina se deseja usar (true) ou não (false) a credibilidade no sistema. | true |
| useLatestCredibility | Determina se é para usar (true) ou não (false) a credibilidade mais recente para o cálculo da reputação. | true |
| debugModeValue | Modo depuração. | true |

## Licença
[GPL-3.0 License](./LICENSE)
