import jade.core.AID;
import jade.core.Agent;
import jade.core.behaviours.Behaviour;
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

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static java.lang.Integer.parseInt;

public class Manager extends Agent {

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
            Thread.sleep(20000);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        addBehaviour(new BlackListWaiter());
    }


    public class BlackListWaiter extends Behaviour {
        HashMap<String, String> compukterList = new HashMap<>();

        @Override
        public void action() {
            ACLMessage msg = myAgent.receive();
            if (msg != null)
                switch (msg.getPerformative()) {
                    case ACLMessage.CONFIRM:
                        compukterList.put(msg.getSender().toString(), msg.getContent());
                        break;
                    case ACLMessage.CANCEL:
                        compukterList.remove(msg.getSender().toString());
                        break;
                }
            else {
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
                if (result.length == compukterList.size()) {
                    ACLMessage STOP_message = new ACLMessage(ACLMessage.CANCEL);
                    for (DFAgentDescription description : result) {
                        STOP_message.addReceiver(description.getName());
                    }
                    myAgent.send(STOP_message);

                    FileWriter writer = null;
                    try {
                        writer = new FileWriter("out.txt");
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                    for (DFAgentDescription description : result) {
                        String[] info = compukterList.get(description.getName().toString()).split("_");
                        String name = info[0];
                        String capacity = info[1];
                        String timeOfWork = info[2];
                        try {
                            writer.write(name + " s:" + capacity + " t:" + timeOfWork + "\n");
                        } catch (IOException e) {
                            e.printStackTrace();
                        }

                        for (int i = 3; i < info.length; i++) {
                            try {
                                writer.write("  " + info[i] + "\n");
                            } catch (IOException e) {
                                e.printStackTrace();
                            }
                        }
                        try {
                            writer.write("\n\n");
                        } catch (IOException e) {
                            e.printStackTrace();
                        }


                    }
                    try {
                        writer.close();
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                } else
                    block();
            }
        }

        @Override
        public boolean done() {
            return false;
        }
    }
}
