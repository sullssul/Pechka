import jade.core.AID;
import jade.core.Agent;
import jade.core.behaviours.Behaviour;
import jade.domain.DFService;
import jade.domain.FIPAAgentManagement.DFAgentDescription;
import jade.domain.FIPAAgentManagement.ServiceDescription;
import jade.domain.FIPAException;
import jade.lang.acl.ACLMessage;
import jade.lang.acl.MessageTemplate;

import java.util.*;

import static java.lang.Integer.parseInt;

public class CompukterAgent extends Agent {
    ArrayList<Task> taskAgentList=new ArrayList<>();
    ArrayList<Double> taskUnitCostList = new ArrayList<>();

    int capacity;



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
//        try {
//            Thread.sleep(20000);
//        } catch (InterruptedException e) {
//            e.printStackTrace();
//        }
        this.addBehaviour(new Requester());

    }

    public class Requester extends Behaviour {
        //компьютеры, с которыми не состоялся обмен
        HashSet<AID> blackList = new HashSet<>();
        private int step = 0;
        Boolean STOP = false;

        private int tasksExpected = 0; //число агентов, которые должны придти к нам после обмена (шаг 2)
        private int tasksReceived = 0;
        private AID tradeAgent = null; //агент, с которым мы меняемся, нужно чтобы не отправлять ему уведомление об обновлении
        //0 - обычный режим (принимаем НОВЫЕ ДЛЯ ВСЕХ задания, обновляем черный список, ищем доступные компы)
        //1 - ждем ответа на наше предложение обмена, забиваем на всё остальное
        //2 - принимаем задания, которые хотят к нам приписаться после обмена

        @Override
        public void action() {
            switch(step){
            case 0:
                MessageTemplate mtp = MessageTemplate.or(MessageTemplate.or(MessageTemplate.or(MessageTemplate.MatchPerformative(ACLMessage.PROPAGATE),
                        MessageTemplate.MatchPerformative(ACLMessage.CFP)), MessageTemplate.MatchPerformative(ACLMessage.PROPOSE)),
                        MessageTemplate.MatchPerformative(ACLMessage.CANCEL));
                ACLMessage msg = myAgent.receive(mtp);
                if (msg != null) {
                    switch (msg.getPerformative()) {
                        //при типе сообщения REQUEST добавляем агент к компьютеру
                        case ACLMessage.PROPAGATE:
                            int cap = parseInt(msg.getContent());
                            AID task_aid = msg.getSender();
                            Task task = new Task(task_aid, cap);
                            taskAgentList.add(task);
                            taskUnitCostList.add(task.complexity / (double)capacity);
                            taskAgentList.sort(new Comparator<Task>() {
                                @Override
                                public int compare(Task o1, Task o2) {
                                    return Integer.compare(o1.complexity, o2.complexity);
                                }
                            });
                            taskUnitCostList.sort(new Comparator<Double>() {
                                @Override
                                public int compare(Double o1, Double o2) {
                                    return o1.compareTo(o2);
                                }
                            });
                            NotifyAllCompuktersExcept(null);
                            break;
                            //при INFORM удаляем комп, который обновился, из ЧС
                        case ACLMessage.CFP:
                            blackList.remove(msg.getSender());
                            break;
                        case ACLMessage.PROPOSE:
                            PerformExchange(msg);
                            break;
                        case ACLMessage.CANCEL:
                            Print();
                            STOP = true;
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
                    //ищем рандомный подходящий комп
                    ArrayList<Integer> indexes = new ArrayList<>();
                    for (int i = 0; i < result.length; i++) {
                        AID res = result[i].getName();
                        if (!blackList.contains(res) && !res.toString().equals(myAgent.getAID().toString()))
                            indexes.add(i);
                    }
                    Random rand = new Random();
                    if (indexes.size() > 0) firstSuitableComp = result[rand.nextInt(indexes.size())].getName();
                    if (firstSuitableComp != null)
                        ProposeExchange(firstSuitableComp);
                    else
                        block();
                }
                break;
            case 1:
                //ждем сообщения согласия на согласие или непринятие обмена, также отказываем всем кто хочет поменяться
                MessageTemplate mt = MessageTemplate.or(MessageTemplate.or(MessageTemplate.or(MessageTemplate.MatchPerformative(ACLMessage.REJECT_PROPOSAL),
                        MessageTemplate.MatchPerformative(ACLMessage.ACCEPT_PROPOSAL)), MessageTemplate.MatchPerformative(ACLMessage.REFUSE)),
                        MessageTemplate.MatchPerformative(ACLMessage.PROPOSE));
                ACLMessage reply = myAgent.receive(mt);

                if (reply != null)
                    switch(reply.getPerformative()) {
                        case ACLMessage.ACCEPT_PROPOSAL:
                            blackList.add(reply.getSender());
                            String repl = reply.getContent();
                            String typeOfTrade = repl.substring(0, repl.indexOf("_"));
                            int val = Integer.parseInt(repl.substring(repl.indexOf("_") + 1));
                            //если нам отсылают задания готовимся принять
                            if (typeOfTrade.equals("getReady")) {
                                tasksExpected = val;
                                tradeAgent = reply.getSender();
                                step = 2;
                            }
                            //если просят нас отсылаем задания и возвращаемся в обычный режим
                            if (typeOfTrade.equals("sendMe")) {
                                ACLMessage req_to_tasks_msg = new ACLMessage();
                                req_to_tasks_msg.setContent(reply.getSender().toString());
                                //рассылаем сообщения заданиям чтобы они приписались к другому агенту и удаляем их у себя
                                for (int i = 0; i < val; i++)
                                    req_to_tasks_msg.addReceiver(taskAgentList.get(i).aid);
                                taskAgentList.subList(0,val).clear();
                                taskUnitCostList.subList(0, val).clear();
                                step = 0;
                                myAgent.send(req_to_tasks_msg);

                                NotifyAllCompuktersExcept(reply.getSender());
                            }
                            break;
                        case ACLMessage.REJECT_PROPOSAL:
                            blackList.add(reply.getSender());
                            step = 0;
                            break;
                        case ACLMessage.REFUSE:
                            step = 0;
                            break;
                        case ACLMessage.PROPOSE:
                            ACLMessage reject_msg = new ACLMessage(ACLMessage.REFUSE);
                            reject_msg.addReceiver(reply.getSender());
                            myAgent.send(reject_msg);
                            break;
                    }
                else
                    block();
                break;
            case 2:
                MessageTemplate Mt = MessageTemplate.or(MessageTemplate.MatchPerformative(ACLMessage.SUBSCRIBE),
                        MessageTemplate.MatchPerformative(ACLMessage.PROPOSE));
                ACLMessage trade_mes = myAgent.receive(Mt);
                if (trade_mes != null)
                switch (trade_mes.getPerformative()) {
                    case ACLMessage.SUBSCRIBE:
                        tasksReceived++;
                        int cap = parseInt(trade_mes.getContent());
                        AID task_aid = trade_mes.getSender();
                        Task task = new Task(task_aid, cap);
                        taskAgentList.add(task);
                        taskUnitCostList.add(task.complexity / (double)capacity);
                        taskAgentList.sort(new Comparator<Task>() {
                            @Override
                            public int compare(Task o1, Task o2) {
                                return Integer.compare(o1.complexity, o2.complexity);
                            }
                        });
                        taskUnitCostList.sort(new Comparator<Double>() {
                            @Override
                            public int compare(Double o1, Double o2) {
                                return o1.compareTo(o2);
                            }
                        });

                        //если получили все задания возвращаемся в обычный режим
                        if (tasksReceived == tasksExpected) {
                            tasksReceived = 0;
                            step = 0;
                            NotifyAllCompuktersExcept(tradeAgent);
                        }
                        break;
                    case ACLMessage.PROPOSE:
                        ACLMessage reject_msg = new ACLMessage(ACLMessage.REFUSE);
                        reject_msg.addReceiver(trade_mes.getSender());
                        myAgent.send(reject_msg);
                        break;
                }
                else
                    block();
                break;
            }
        }

        public void Print() {
            StringBuilder outp = new StringBuilder();
            for (Task tsk : taskAgentList) {
                outp.append(tsk.complexity).append(" ");
            }
            StringBuilder outpp = new StringBuilder();
            double sum = 0;
            for (double k : taskUnitCostList) {
                outpp.append(String.format(Locale.US,"%.2f", k)).append(" ");
                sum += k;
            }
            //System.out.println(getLocalName() + " ГОВОРИТ " + outp);
            System.out.println(getLocalName() + " N: " + taskUnitCostList.size() + " t: " + String.format(Locale.US,"%.2f", sum) + " contains:" + outp);
        }

        public void PerformExchange(ACLMessage msg) {
            //System.out.println("ПОПЫТКА " + getLocalName() + "<--->" + msg.getSender().getLocalName());
            blackList.add(msg.getSender());
            String[] input = msg.getContent().split(" ");
            List<Integer> ext_tasks_complexity = new ArrayList<>();
            List<Double> ext_unit_cost = new ArrayList<>();
            int ext_capacity = parseInt(input[0]);
            int sum_complexity = 0;

            for (int i = 1; i < input.length; i++) {
                int k = parseInt(input[i]);
                sum_complexity += k;
                ext_tasks_complexity.add(k);
                ext_unit_cost.add(k / (double) ext_capacity);
            }
            double ext_TimeOfWork = sum_complexity / (double) ext_capacity;
            double our_TimeOfWork = getTimeOfWork();


            //если мы работаем больше чем другой агент, то пытаемся отдать ему задания, начиная с самых простых
            if (ext_TimeOfWork < our_TimeOfWork) {
                int index_of_last_suitable_task = 0;
                int size = taskAgentList.size();

                while (our_TimeOfWork > ext_TimeOfWork && index_of_last_suitable_task < size) {
                    //предполагаем как изменится время если мы отдадим задание
                    double ext_comp_gain = taskAgentList.get(index_of_last_suitable_task).complexity / (double)ext_capacity;
                    double our_comp_loss = taskUnitCostList.get(index_of_last_suitable_task);
                    //если мы освободили больше времени, чем получил другой агент, то меняемся
                        our_TimeOfWork -= our_comp_loss;
                        ext_TimeOfWork += ext_comp_gain;
                        index_of_last_suitable_task++;

                    //если освободили меньше времени, чем получил другой агент, то не меняемся

                }
                //прикидываем стоит ли выкинуть последнее взятое задание
                if (size > 0) {
                    double ext_comp_gain = taskAgentList.get(index_of_last_suitable_task - 1).complexity / (double) ext_capacity;
                    double our_comp_loss = taskUnitCostList.get(index_of_last_suitable_task - 1);
                    if (Math.abs(our_TimeOfWork + our_comp_loss - ext_TimeOfWork + ext_comp_gain) < Math.abs(our_TimeOfWork - ext_TimeOfWork))
                        index_of_last_suitable_task--;
                }
                //если нашли подходящие задания
                if (index_of_last_suitable_task > 0) {
                    //отправляем сообщение чтобы агент готовился к приему заданий
                    ACLMessage accept_message = new ACLMessage(ACLMessage.ACCEPT_PROPOSAL);
                    accept_message.setContent("getReady_" + index_of_last_suitable_task);
                    accept_message.addReceiver(msg.getSender());
                    myAgent.send(accept_message);

                    ACLMessage message = new ACLMessage();
                    message.setContent(msg.getSender().toString());
                    //рассылаем сообщения заданиям чтобы они приписались к другому агенту и удаляем их у себя
                    for (int i = 0; i < index_of_last_suitable_task; i++)
                        message.addReceiver(taskAgentList.get(i).aid);
                    taskAgentList.subList(0,index_of_last_suitable_task).clear();
                    taskUnitCostList.subList(0, index_of_last_suitable_task).clear();
                    myAgent.send(message);
                    System.out.println(getLocalName() + "--->" + msg.getSender().getLocalName());
                    //уведомляем всех о своём изменении
                    NotifyAllCompuktersExcept(msg.getSender());
                    return;
                }
            } else
            //если мы работаем меньше чем другой агент, пытаемся кого нибудь взять
            if (ext_TimeOfWork > our_TimeOfWork) {
                int index_of_last_suitable_task = 0;
                int size = ext_unit_cost.size();

                while (our_TimeOfWork < ext_TimeOfWork && index_of_last_suitable_task < size) {
                    //предполагаем как изменится время если мы ВОЗЬМЕМ задание
                    double ext_comp_loss = ext_unit_cost.get(index_of_last_suitable_task);
                    double our_comp_gain = ext_tasks_complexity.get(index_of_last_suitable_task) / (double) capacity;
                    //если другой агент освободил больше времени, чем мы получили, то меняемся

                        our_TimeOfWork += our_comp_gain;
                        ext_TimeOfWork -= ext_comp_loss;
                        index_of_last_suitable_task++;

                    //если освободили меньше времени, чем получил другой агент, то не меняемся

                }
                //прикидываем стоит ли выкинуть последнее задание
                if (size > 0) {
                    double ext_comp_loss = ext_unit_cost.get(index_of_last_suitable_task - 1);
                    double our_comp_gain = ext_tasks_complexity.get(index_of_last_suitable_task - 1) / (double) capacity;
                    if (Math.abs(ext_TimeOfWork + ext_comp_loss - our_TimeOfWork + our_comp_gain) < Math.abs(ext_TimeOfWork - our_TimeOfWork))
                        index_of_last_suitable_task--;
                }
                //если нашли подходящее
                if (index_of_last_suitable_task > 0) {
                    //отправляем сообщение чтобы агент отправил нам задания, сами готовимся к их приему
                    ACLMessage accept_message = new ACLMessage(ACLMessage.ACCEPT_PROPOSAL);
                    accept_message.setContent("sendMe_" + index_of_last_suitable_task);
                    accept_message.addReceiver(msg.getSender());
                    tradeAgent = msg.getSender();
                    tasksExpected = index_of_last_suitable_task;
                    step = 2;
                    System.out.println(getLocalName() + "<---" + msg.getSender().getLocalName());
                    myAgent.send(accept_message);
                    return;
                }
            }
            //////ЕСЛИ НИЧЕГО ПОДХОДЯЩЕГО НЕ НАШЛИ ОТПРАВЛЯЕМ REJECT
            ACLMessage reject_message = new ACLMessage(ACLMessage.REJECT_PROPOSAL);
            reject_message.addReceiver(msg.getSender());
            myAgent.send(reject_message);
        }

        public void ProposeExchange(AID comp) {
            ACLMessage message = new ACLMessage(ACLMessage.PROPOSE);
            StringBuilder myinfo = new StringBuilder(capacity + " ");
            for (Task task : taskAgentList)
                myinfo.append(task.complexity).append(" ");
            message.setContent(myinfo.toString());
            message.addReceiver(comp);
            step = 1;
            myAgent.send(message);
        }

        //уведомляем все компьютеры, кроме переданного, о своём обновлении (вызываем когда делаем обмен или добавляем задание)
        public void NotifyAllCompuktersExcept(AID excludedAgent) {
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
            ACLMessage message = new ACLMessage(ACLMessage.CFP);
            for (DFAgentDescription description : result)
                if (!description.getName().toString().equals(myAgent.getAID().toString()) && excludedAgent != null && !description.getName().toString().equals(excludedAgent.toString())) message.addReceiver(description.getName());


//                //менеджера тоже уведомляем
//            AID manager = null;
//            template = new DFAgentDescription();
//            sd = new ServiceDescription();
//            sd.setType("manager");
//            template.addServices(sd);
//            try {
//                DFAgentDescription[] res = DFService.search(myAgent, template);
//                if (result.length != 0) {
//                    manager = res[0].getName();
//                } else {
//                    return;
//                }
//            } catch (FIPAException fe) {
//                fe.printStackTrace();
//            }
//            message.addReceiver(manager);
//            message.setContent(String.valueOf(getTimeOfWork()));
            Print();

            myAgent.send(message);
        }

        public void checkStopCondition() {

        }

        @Override
        public boolean done() {
            return STOP;
        }
    }

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
