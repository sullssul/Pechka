import jade.core.AID;
import jade.core.Agent;
import jade.core.behaviours.OneShotBehaviour;
import jade.domain.DFService;
import jade.domain.FIPAAgentManagement.DFAgentDescription;
import jade.domain.FIPAAgentManagement.ServiceDescription;
import jade.domain.FIPAException;
import jade.lang.acl.ACLMessage;

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


        @Override
        public void action() {

            AID compukter=null;
            DFAgentDescription template = new DFAgentDescription();
            ServiceDescription sd = new ServiceDescription();
            sd.setType("compukter");
            template.addServices(sd);

            try {
                DFAgentDescription[] result = DFService.search(myAgent, template);
                compukter= result[0].getName();
            } catch (FIPAException fe) {
                fe.printStackTrace();
            }

            ACLMessage message = new ACLMessage(ACLMessage.INFORM);
            message.addReceiver(compukter);
           // message.setContent(complexity+"_"+myAgent.getAID());
            message.setReplyWith(("ready" + System.currentTimeMillis()));
            System.out.println(myAgent.getLocalName()+" Send message to first pk");
            myAgent.send(message);
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
