/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.cc86.MMC.server;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.net.Socket;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.cc86.MMC.API.API;
import org.cc86.MMC.API.Handler;
import org.cc86.MMC.API.Request;
import org.yaml.snakeyaml.Yaml;


/**
 *
 * @author tgoerner
 */
public class TCPhandler implements Handler{
    Socket sck;
    String returnMsg;
    public TCPhandler(Socket s)
    {
        sck=s;
    }
    
    public void start()
    {
        try {
            BufferedReader r = new BufferedReader(new InputStreamReader(sck.getInputStream()));
            PrintStream out = new PrintStream(sck.getOutputStream());
            Thread t = new Thread(()->{
                while(sck.isConnected())
                {
                    String request = "";
                    try {
                        String ln = r.readLine();
                        while(!ln.equals("---"))
                        {
                            request+=ln+"\n";
                        }
                        
                    } catch (IOException ex) {
                        Logger.getLogger(TCPhandler.class.getName()).log(Level.SEVERE, null, ex);
                    }
                    Object packet = new Yaml();
                    if(packet instanceof Request)
                    {
                        
                    }
                    else
                    {
                        //error();
                    }
               }
            });
            t.setName("InputCruncher");
            Thread t2 = new Thread(()->{
                while(sck.isConnected())
                {
                    synchronized(returnMsg)
                    {
                        try {
                            returnMsg.wait();
                        } catch (InterruptedException ex) {
                        }
                        out.println(returnMsg);
                        returnMsg="";
                    }
                }
            });
            t2.setName("OutputCruncher");
            
        } catch (IOException ex) {
            Logger.getLogger(TCPhandler.class.getName()).log(Level.SEVERE, null, ex);
        }
    }    

    @Override
    public void respondToLinkedClient(Request response) {
         API.getDispatcher().handleEvent(response);
    }
    
    
}
