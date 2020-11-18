import jade.core.AID;
import jade.core.Agent;
import jade.core.behaviours.Behaviour;
import jade.core.behaviours.CyclicBehaviour;
import jade.core.behaviours.OneShotBehaviour;
import jade.domain.DFService;
import jade.domain.FIPAAgentManagement.DFAgentDescription;
import jade.domain.FIPAAgentManagement.ServiceDescription;
import jade.domain.FIPAException;
import jade.lang.acl.ACLCodec;
import jade.lang.acl.ACLMessage;
import jade.lang.acl.MessageTemplate;
import jade.lang.acl.StringACLCodec;

import java.io.StringReader;
import java.util.HashSet;

import static java.lang.Integer.parseInt;

public class TaskAgent extends Agent {

    int complexity;

    @Override
    protected void setup() {
        System.out.println("Task " + getLocalName()+" is ready");

        Object[] args = getArguments();

        if (args != null && args.length == 2) {
            complexity = parseInt(args[0].toString());

        } else {
            /* Удаляем агента если не заданы параметры */
            this.takeDown();
        }

        DFAgentDescription dfd = new DFAgentDescription();
        dfd.setName(getAID());
        ServiceDescription sd = new ServiceDescription();
        sd.setType("task");
        sd.setName("MyTask");
        dfd.addServices(sd);
        try {
            DFService.register(this, dfd);
        } catch (FIPAException fe) {
            fe.printStackTrace();
        }
        addBehaviour(new InitBehaviour());
    }

    public class InitBehaviour extends OneShotBehaviour{

        //отправляем сообщения менеджеру и компьютеру о своём создании
        @Override
        public void action() {
            //сначала ищем компик
            AID compukter = null;
            DFAgentDescription template = new DFAgentDescription();
            ServiceDescription sd = new ServiceDescription();
            sd.setType("compukter");
            template.addServices(sd);
            try {
                DFAgentDescription[] result = DFService.search(myAgent, template);
                compukter = result[0].getName();
            } catch (FIPAException fe) {
                fe.printStackTrace();
            }
            //потом ищем манагера (скорее всего не нужно будет)
            AID manager = null;
            template = new DFAgentDescription();
            sd = new ServiceDescription();
            sd.setType("manager");
            template.addServices(sd);
            try {
                DFAgentDescription[] result = DFService.search(myAgent, template);
                if (result.length != 0) {
                    manager = result[0].getName();
                } else {
                    return;
                }
            } catch (FIPAException fe) {
                fe.printStackTrace();
            }

            ACLMessage message = new ACLMessage(ACLMessage.REQUEST);
            message.addReceiver(manager);
            message.addReceiver(compukter);
            message.setContent(String.valueOf(complexity));
            myAgent.send(message);
            addBehaviour(new ChangeCompukterBehaviour());
        }
    }

    //когда совершается обмен компьютер просит агент задания прикрепиться к другому
    public class ChangeCompukterBehaviour extends CyclicBehaviour {

        @Override
        public void action() {
            ACLMessage reply = myAgent.receive();
            if (reply != null) {
                StringACLCodec codec = new StringACLCodec(new StringReader(reply.getContent()), null);
                AID compukter = null;
                try {
                    compukter = codec.decodeAID();
                } catch (ACLCodec.CodecException e) {
                    e.printStackTrace();
                }
                ACLMessage message = new ACLMessage(ACLMessage.SUBSCRIBE);
                message.addReceiver(compukter);
                message.setContent(String.valueOf(complexity));
                myAgent.send(message);
            } else
                block();
        }

    }

    @Override
    protected void takeDown() {
        try {
            DFService.deregister(this);
        } catch (FIPAException fe) {
            fe.printStackTrace();
        }
    }
}
