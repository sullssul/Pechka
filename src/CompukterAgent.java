import jade.core.AID;
import jade.core.Agent;
import jade.core.behaviours.Behaviour;
import jade.core.behaviours.TickerBehaviour;
import jade.domain.DFService;
import jade.domain.FIPAAgentManagement.DFAgentDescription;
import jade.domain.FIPAAgentManagement.ServiceDescription;
import jade.domain.FIPAException;
import jade.lang.acl.ACLCodec;
import jade.lang.acl.ACLMessage;
import jade.lang.acl.MessageTemplate;
import jade.lang.acl.StringACLCodec;

import java.io.StringReader;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;

import static java.lang.Integer.parseInt;

public class CompukterAgent extends Agent {

    List<Task> taskAgentList=new ArrayList<>();
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

        this.addBehaviour(new Requester());

    }

    public class Requester extends Behaviour {
        //компьютеры, с которыми не состоялся обмен
        HashSet<AID> blackList = new HashSet<>();
        private int step = 0;
        //0 - обычный режим (принимаем новые задания, ищем доступные компы)
        //1 - ждем ответа на наше предложение обмена, забиваем на всё остальное
        //2 - (возможно) выход

        @Override
        public void action() {
            switch(step){
            case 0:
                ACLMessage msg = myAgent.receive();
                if (msg != null) {
                    switch (msg.getPerformative()) {
                        //при типе сообщения REQUEST добавляем агент к компьютеру (еще к менеджеру)
                        case ACLMessage.REQUEST:
                            int cap = parseInt(msg.getContent());
                            AID task_aid = msg.getSender();
                            Task task = new Task(task_aid, cap);
                            taskAgentList.add(task);
                            NotifyAllCompukters();
                            System.out.println(getLocalName() + " взял " + task_aid.getLocalName());
                            break;
                            //при INFORM удаляем комп, который обновился, из ЧС
                        case ACLMessage.INFORM:
                            blackList.remove(msg.getSender());
                            break;
                        case ACLMessage.PROPOSE:

                            break;
                    }
                } else {
                    //если сообщений нет то ищем компы не из черного списка
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
                    AID firstSuitableComp = null;
                    //ищем первый попавшийся подходящий комп
                    for (int i = 0; i < result.length; i++) {
                        AID res = result[i].getName();
                        if (!blackList.contains(res) && res != myAgent.getAID()) {
                            firstSuitableComp = res;
                            break;
                        }
                    }
                    PerformExchange(firstSuitableComp);


                    block();
                }
                break;
            case 1:
                //ждем сообщения согласия на обмен либо отказа
                MessageTemplate mt = MessageTemplate.or(MessageTemplate.MatchPerformative(ACLMessage.REFUSE),
                        MessageTemplate.MatchPerformative(ACLMessage.ACCEPT_PROPOSAL));
                ACLMessage reply = myAgent.receive(mt);

                if (reply != null)
                    switch (reply.getPerformative()) {
                        case ACLMessage.ACCEPT_PROPOSAL:

                            break;
                        case ACLMessage.REFUSE:

                            break;
                }
                else
                    block();

                break;
            }
        }

        public void Calculate_profit(String tasks) {
            String[] input = tasks.split(" ");
            List<Integer> tasks_complexity = new ArrayList<>();
            int exc_capacity = parseInt(input[0]);
            List<Double> unit_cost = new ArrayList<>();
            for (int i = 1; i < input.length; i++) {
                int k = parseInt(input[i]);
                tasks_complexity.add(k);
                unit_cost.add((double) (k / exc_capacity));
            }
            //////ВОТ ТУТ Я ОСТАНОВИЛСЯ ПОКА

        }

        public void PerformExchange(AID comp) {
            ACLMessage message = new ACLMessage(ACLMessage.PROPOSE);
            String myinfo = capacity + " ";
            for (Task task : taskAgentList)
                myinfo += task.complexity + " ";
            message.setContent(myinfo);
            message.addReceiver(comp);
            step = 1;
            myAgent.send(message);
        }

        //уведомляем все компьютеры о своём обновлении (вызываем когда делаем обмен или добавляем задание)
        public void NotifyAllCompukters() {
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
            ACLMessage message = new ACLMessage(ACLMessage.INFORM);
            for (DFAgentDescription description : result)
                if (description.getName() != myAgent.getAID()) message.addReceiver(description.getName());
            myAgent.send(message);
        }

        @Override
        public boolean done() {
            return false;
        }
    }

//public class TaskRequester extends TickerBehaviour {
//
//    boolean isMessageSendToManager = false;
//    int step=0;
//
//    public TaskRequester(Agent a, long period) {
//        super(a, period);
//    }
//
//    @Override
//    protected void onTick() {
//        if (isMessageSendToManager) {
//            return;
//        }
//
//
//        ACLMessage msg = receive();
//        if (msg != null) {
//            AID task= msg.getSender();
//            //taskAgentList.add(task);
//            System.out.println(msg.getSender());
//        }
//    }
//}


    public double getTimeOfWork(){
        double sum_complexity = 0;
        for (Task task: taskAgentList)
            sum_complexity += task.complexity;
        return sum_complexity/capacity;
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
