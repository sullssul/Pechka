import jade.core.AID;
import jade.core.Agent;
import jade.core.behaviours.CyclicBehaviour;
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
    }

    public class TasksReceiver extends CyclicBehaviour {


        @Override
        public void action() {
            ACLMessage msg = myAgent.receive();
            if (msg != null) {
                switch (msg.getPerformative()) {
                    //при типе сообщения REQUEST добавляем агент к менеджеру (помимо компьютера)
                    case ACLMessage.REQUEST:
                        int cap = parseInt(msg.getContent());
                        AID task_aid = msg.getSender();
                        Task task = new Task(task_aid, cap);
                        tasks.add(task);
                }
            } else {
                block();
            }
        }
    }
}
