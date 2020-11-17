import jade.core.AID;
import jade.core.Agent;
import jade.core.BehaviourID;
import jade.core.behaviours.Behaviour;
import jade.core.behaviours.TickerBehaviour;
import jade.domain.DFService;
import jade.domain.FIPAAgentManagement.DFAgentDescription;
import jade.domain.FIPAAgentManagement.ServiceDescription;
import jade.domain.FIPAException;
import jade.lang.acl.ACLMessage;

import java.util.ArrayList;
import java.util.List;

import static java.lang.Integer.parseInt;

public class CompukterAgent extends Agent {

    List<AID> taskAgentList=new ArrayList<>();
    public double average;
    public double timeOfWork;

    int capacity;
    double totalComplexity = 0;



    @Override
    protected void setup() {
        System.out.println("Compukter " + getLocalName()+" is ready");

        Object[] args = getArguments();

        if (args != null && args.length == 2) {
            capacity = parseInt(args[0].toString());

        } else {
            /* Удаляем агента если не заданы параметры */
            this.takeDown();
        }

        DFAgentDescription dfd = new DFAgentDescription();
        dfd.setName(getAID());
        ServiceDescription sd = new ServiceDescription();
        sd.setType("compukter");
        sd.setName("myCompukter");
        dfd.addServices(sd);
        try {
            DFService.register(this, dfd);
        } catch (FIPAException fe) {
            fe.printStackTrace();
        }

        this.addBehaviour(new TaskRequester(this, 100));

    }

public class TaskRequester extends TickerBehaviour {

    boolean isMessageSendToManager = false;
    int step=0;

    public TaskRequester(Agent a, long period) {
        super(a, period);
    }

    @Override
    protected void onTick() {
        if (isMessageSendToManager) {
            return;
        }


        ACLMessage msg = receive();
        if (msg != null) {
            AID task= msg.getSender();
            taskAgentList.add(task);
            System.out.println(msg.getSender());
        }
    }
}


    public void getTimeOfWork(){
        timeOfWork=totalComplexity/capacity;
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
