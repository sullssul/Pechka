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

        this.addBehaviour(new Requester());

    }

    public class Requester extends Behaviour {
        //компьютеры, с которыми не состоялся обмен
        HashSet<AID> blackList = new HashSet<>();
        private int step = 0;

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
                //MessageTemplate mtp = MessageTemplate.or(MessageTemplate.or(MessageTemplate.MatchPerformative(ACLMessage.REQUEST),
                //        MessageTemplate.MatchPerformative(ACLMessage.INFORM)), MessageTemplate.MatchPerformative(ACLMessage.PROPOSE));
                ACLMessage msg = myAgent.receive();
                if (msg != null) {
                    switch (msg.getPerformative()) {
                        //при типе сообщения REQUEST добавляем агент к компьютеру
                        case ACLMessage.REQUEST:
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
                            System.out.println(getLocalName() + " взял " + task_aid.getLocalName());
                            NotifyAllCompuktersExcept(null);









                            ///////////ВЫВОД СОСТОЯНИЯ АГЕНТА
                            Print();
                            ///////////////////////////////////////







                            break;
                            //при INFORM удаляем комп, который обновился, из ЧС
                        case ACLMessage.INFORM:
                            blackList.remove(msg.getSender());
                            break;
                        case ACLMessage.PROPOSE:
                            PerformExchange(msg);
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
                    if (firstSuitableComp != null)
                        ProposeExchange(firstSuitableComp);
                    else
                        block();
                }
                break;
            case 1:
                //ждем сообщения согласия на согласие или непринятие обмена, также отказываем всем кто хочет поменяться
                MessageTemplate mt = MessageTemplate.or(MessageTemplate.or(MessageTemplate.or(MessageTemplate.MatchPerformative(ACLMessage.REJECT_PROPOSAL),
                        MessageTemplate.MatchPerformative(ACLMessage.ACCEPT_PROPOSAL)), MessageTemplate.MatchPerformative(ACLMessage.PROPOSE)),
                        MessageTemplate.MatchPerformative(ACLMessage.REFUSE));
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











                                ///////////ВЫВОД СОСТОЯНИЯ АГЕНТА
                                Print();
                                ///////////////////////////////////////








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

                if (trade_mes != null) {
                    if (trade_mes.getPerformative() == ACLMessage.PROPOSE) {
                        ACLMessage reject_msg = new ACLMessage(ACLMessage.REFUSE);
                        reject_msg.addReceiver(trade_mes.getSender());
                        myAgent.send(reject_msg);
                        return;
                    }
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
                    System.out.println(getLocalName() + " взял " + task_aid.getLocalName());
                    NotifyAllCompuktersExcept(null);






                    ///////////ВЫВОД СОСТОЯНИЯ АГЕНТА
                    Print();
                    ///////////////////////////////////////





                    //если получили все задания возвращаемся в обычный режим
                    if (tasksReceived == tasksExpected) {
                        step = 0;
                        NotifyAllCompuktersExcept(tradeAgent);
                    }
                } else
                    block();
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
                outpp.append(k).append(" ");
                sum += k;
            }
            //System.out.println(getLocalName() + " ГОВОРИТ " + outp);
            System.out.println(getLocalName() + " t: " + sum + " contains:" + outpp);
        }

        public void PerformExchange(ACLMessage msg) {
            blackList.add(msg.getSender());
            String[] input = msg.getContent().split(" ");
            List<Integer> tasks_complexity = new ArrayList<>();
            List<Double> ext_unit_cost = new ArrayList<>();
            int ext_capacity = parseInt(input[0]);
            int sum_complexity = 0;

            for (int i = 1; i < input.length; i++) {
                int k = parseInt(input[i]);
                sum_complexity += k;
                tasks_complexity.add(k);
                ext_unit_cost.add((double) (k / ext_capacity));
            }
            double ext_TimeOfWork = sum_complexity / ext_capacity;
            double our_TimeOfWork = getTimeOfWork();


            //если мы работаем больше чем другой агент, то пытаемся отдать ему задания, начиная с самых простых
            if (ext_TimeOfWork < our_TimeOfWork) {
                double diff_time = (our_TimeOfWork - ext_TimeOfWork) / 2;
                int index_of_last_suitable_task = 0;
                int size = taskAgentList.size();

                while (diff_time >= 0 && index_of_last_suitable_task < size) {
                    //предполагаем как изменится время если мы отдадим задание
                    double ext_comp_gain = taskAgentList.get(index_of_last_suitable_task).complexity / (double)ext_capacity;
                    double our_comp_loss = taskUnitCostList.get(index_of_last_suitable_task);
                    //если мы освободили больше времени, чем получил другой агент, то меняемся
                    if (our_comp_loss > ext_comp_gain){
                        diff_time = diff_time + ext_comp_gain - our_comp_loss;
                        index_of_last_suitable_task++;
                    }
                    //если освободили меньше времени, чем получил другой агент, то не меняемся
                    else
                        break;
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










                    ///////////ВЫВОД СОСТОЯНИЯ АГЕНТА
                    Print();
                    ///////////////////////////////////////







                    //уведомляем всех о своём изменении
                    NotifyAllCompuktersExcept(msg.getSender());
                    return;
                }
            }
            //если мы работаем меньше чем другой агент, пытаемся кого нибудь взять
            else {
                double diff_time = (ext_TimeOfWork - our_TimeOfWork) / 2;
                int index_of_last_suitable_task = 0;
                int size = ext_unit_cost.size();

                while (diff_time >= 0 && index_of_last_suitable_task < size) {
                    //предполагаем как изменится время если мы ВОЗЬМЕМ задание
                    double ext_comp_loss = ext_unit_cost.get(index_of_last_suitable_task);
                    double our_comp_gain = tasks_complexity.get(index_of_last_suitable_task) / (double) capacity;
                    //если другой агент освободил больше времени, чем мы получили, то меняемся
                    if (ext_comp_loss > our_comp_gain){
                        diff_time = diff_time + our_comp_gain - ext_comp_loss;
                        index_of_last_suitable_task++;
                    }
                    //если освободили меньше времени, чем получил другой агент, то не меняемся
                    else
                        break;
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
            ACLMessage message = new ACLMessage(ACLMessage.INFORM);
            for (DFAgentDescription description : result)
                if (description.getName() != myAgent.getAID() && description.getName() != excludedAgent) message.addReceiver(description.getName());
            myAgent.send(message);
        }

        @Override
        public boolean done() {
            return false;
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
