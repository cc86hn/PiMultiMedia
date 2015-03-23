/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */

package org.cc86.MMC.client;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.net.Socket;
import org.cc86.MMC.API.Packet;
import org.cc86.MMC.client.API.Connection;
import org.yaml.snakeyaml.Yaml;

/**
 *
 * @author iZc <nplusc.de>
 */
public class TCPConnection implements Connection
{
    private final int port;
    private final String destination;
    private Socket sck;
    private String returnMsg;
    public TCPConnection(String dest,int pPort)
    {
        port=pPort;
        destination=dest;
    }
    
    public void connect() throws IOException
    {
        sck=new Socket(destination, port);
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
                    }
                    Object packet = new Yaml().load(request);
                    if(packet instanceof Packet)
                    {
                        Main.getDispatcher().sendPacketToModule((Packet)packet);
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
                    synchronized(destination)
                    {
                        try {
                            destination.wait();
                        } catch (InterruptedException ex) {
                        }
                        out.println(returnMsg);
                        out.flush();
                        returnMsg="";
                    }
                }
            });
            t2.setName("OutputCruncher");
            
        } catch (IOException ex) {
        }
    }
    
    
    @Override
    public void sendRequest(Packet response) {
         synchronized(destination)
         {
             returnMsg=new Yaml().dump(response)+"\n---\n";
             destination.notify();
         }
    }
    
    
    
}