import jade.core.Agent;
import jade.domain.DFService;
import jade.domain.FIPAException;

import java.util.ArrayList;
import java.util.List;

public class CompukterAgent extends Agent {

    List<TaskAgent> taskAgentList=new ArrayList<>();
    int capacity;

    @Override
    protected void setup() {
        super.setup();
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
