import jade.core.AID;
import jade.core.Agent;
import jade.core.behaviours.CyclicBehaviour;
import jade.core.behaviours.OneShotBehaviour;
import jade.core.behaviours.TickerBehaviour;
import jade.domain.DFService;
import jade.domain.FIPAAgentManagement.DFAgentDescription;
import jade.domain.FIPAAgentManagement.ServiceDescription;
import jade.domain.FIPAException;
import jade.lang.acl.ACLCodec;
import jade.lang.acl.ACLMessage;
import jade.lang.acl.StringACLCodec;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.List;

import static java.lang.Integer.parseInt;

public class Manager extends Agent {
    List<Task> tasks = new ArrayList<>();

    @Override
    protected void setup() {
        System.out.println(getLocalName()+ " is ready");

        DFAgentDescription dfd = new DFAgentDescription();
        dfd.setName(getAID());
        ServiceDescription sd = new ServiceDescription();
        sd.setType("manager");
        sd.setName("Manager");
        dfd.addServices(sd);
        try {
            DFService.register(this, dfd);
        } catch (FIPAException fe) {
            fe.printStackTrace();
        }
        try {
            Thread.sleep(5000);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        addBehaviour(new GetRespondFromAll());
    }

    public class GetRespondFromAll extends OneShotBehaviour {


        @Override
        public void action() {
            DFAgentDescription template = new DFAgentDescription();
            ServiceDescription sd = new ServiceDescription();
            sd.setType("compukter");
            template.addServices(sd);
            DFAgentDescription[] result = null;
            try {
                result = DFService.search(myAgent, template);
            } catch (FIPAException fe) {
                fe.printStackTrace();
            }
            ACLMessage STOP_message = new ACLMessage(ACLMessage.CANCEL);
            for (DFAgentDescription description : result)
                STOP_message.addReceiver(description.getName());
            System.out.println("ВСЕМ СТОЯТЬ");
            myAgent.send(STOP_message);
        }
    }
}
