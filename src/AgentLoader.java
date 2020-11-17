import jade.core.Agent;
import jade.wrapper.AgentController;
import jade.wrapper.StaleProxyException;

import java.io.*;
import java.util.ArrayList;
import java.util.List;

import static java.lang.Integer.parseInt;

public class AgentLoader extends Agent {

    @Override
    protected void setup() {

        File file = new File("compukteri.txt");
        File file1 = new File("tasks.txt");

        createAgents(file,"CompukterAgent");
        createAgents(file1,"TaskAgent");

        AgentController ac = null;
        try {
            ac = getContainerController().createNewAgent("Manager","Manager",null);
        } catch (StaleProxyException e) {
            e.printStackTrace();
        }

        if (ac != null) {
            try {
                ac.start();
            } catch (StaleProxyException e) {
                e.printStackTrace();
            }
        }






    }

    private void  createAgents(File file1,String typeAgent) {
        List<Object[]> objects;
        objects=  parser(file1);

        for (Object[] object : objects) {

            AgentController ac = null;
            try {

                ac = getContainerController().createNewAgent((String) object[1],typeAgent,object);
            } catch (StaleProxyException e) {
                e.printStackTrace();
            }

            if (ac != null) {
                try {
                    ac.start();
                } catch (StaleProxyException e) {
                    e.printStackTrace();
                }
            }

        }
    }


    public List<Object[]> parser(File file) {
        int cnt;
        List<Object[]> objects=new ArrayList<>();
        FileReader fr = null;
        String line;
        try {
            fr = new FileReader(file);
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        }
        BufferedReader reader = new BufferedReader(fr);
        try {
            line = reader.readLine();
            cnt = parseInt(line);
            line = reader.readLine();
            for (int i = 0; i < cnt; i++) {

                int capacity = parseInt(line.substring(0, line.indexOf("_")));
                String name = line.substring(line.indexOf("_") + 1);
                Object[] args = new Object[]
                        {
                                capacity, name
                        };

                objects.add(args);
                line = reader.readLine();

            }

        } catch (IOException e) {
            e.printStackTrace();
        }

        return objects;

    }
}

