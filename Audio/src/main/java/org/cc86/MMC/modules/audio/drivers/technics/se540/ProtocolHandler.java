/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.cc86.MMC.modules.audio.drivers.technics.se540;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.cc86.MMC.API.CRC;

/**
 *
 * @author tgoerner
 */
 @SuppressWarnings("PointlessBitwiseExpression")
public class ProtocolHandler
{
    private static final Logger l = LogManager.getLogger();
      
    private ArrayList<Byte> receivebuffer = new ArrayList<>();
    private Consumer<List<Byte>> requestResponseListener = null;
    private final Thread handlingThread;
    
    private static final int MAX_PACKET_SIZE=9;
    private static final byte INVERTED_SIZE_MASK = 0x38;
    private static final int INVERTED_SIZE_OFFSET = 3;
    private static final byte CNT_MASK = (byte)0xC0;
    private static final int CNT_OFFSET = 6;
    private static final int CNT_LENGTH = 2;
    private static final int CNT_BYTE = 0;
    private static final byte BASE_SIZE_MASK = 0x07;
    private static final int BASE_SIZE_LENGTH = 3;
    
    private static final int SRV_BYTE = 1;
    private static final int SRV_MASK = 0x07;
    private static final int SRV_LENGTH = 3;
    private static final int SRV_OFFSET = 0;
    public static final int SRV_RET = 0;
    public static final int SRV_SET = 1;
    public static final int SRV_GET = 2;
    public static final int SRV_EVT = 3;
    public static final int SRV_SYS = 4;
    
    public static final int CMD_BYTE = 1;
    public static final int CMD_MASK = 0xF8;
    public static final int CMD_OFFSET = 3;
    public static final int CMD_LENGTH = 5;
    
    /*public static final int CMD_VERSION_VER         = 0;
    public static final int CMD_RESET_RST           = 1;
    public static final int CMD_BOOTLOADER_BOOT     = 2;
    public static final int CMD_EVENT_EVT           = 3;
    public static final int CMD_EVENT_EVTALL        = 4;
    public static final int CMD_STATISTIC_STAT      = 5;
    public static final int CMD_POWER_PWR           = 6;
    public static final int CMD_VOLUME_VOL          = 7;
    public static final int CMD_VOLUME_VOLREL       = 8;
    public static final int CMD_VOLUME_MUTE         = 9;
    public static final int CMD_VOLUME_BALREL       = 10;
    public static final int CMD_VOLUME_BAL          = 11;
    public static final int CMD_SOURCE_SRC          = 12;
    public static final int CMD_SPEAKER_SPK         = 13;
    public static final int CMD_SPEAKER_SPKCFG      = 14;
    public static final int CMD_TIME_TIME           = 15;*/
    
 
   
    
    public final List<DataHandler> dataHandlers;
    
    
    private static final int USRDATA_REQ_START_BYTE=2;
    
    
    private static final int USRDATA_RET_START_BYTE=3;
    private static final int RET_ST_BYTE = 1;
    private static final int RET_ST_MASK = 0x18;
    private static final int RET_ST_OFFSET = 3;
    private static final int RET_ST_LENGTH = 2;
    private static final int RET_ST_ACK = 1;
    
    private static final int RET_ST_PND = 2;
    private static final int RET_ST_NACK = 3;
    private static final int REQ_CRC_BYTE = 1;
    
    /*package*/ static final byte NACK_UNKNOWN = 1;
    
    public static final byte NACK_INVALID = 1;
    
    public static final byte NACK_BUSY = 1;
    
    
    // session handling logic
    private int retries=0;
    private long lastCmdStartTime=0;
    private boolean acked=false;
    boolean sent=false;
    @SuppressWarnings({"SleepWhileInLoop", "LeakingThisInConstructor"})
    public ProtocolHandler(/*StereoControl c*/)
    {
        //setting up the listener lookup array
        dataHandlers = Arrays.asList(
            DataHandlerVersion.linkHandler(this),       //VERSION->VER;NO_EVT
            null,                                       //RESET->RST;NO_EVT
            null,                                       //BOOTLOADER->BOOT;NO_EVT
            null,                                       //EVENT->EVT;NO_EVT
            null,                                       //EVENT->EVTALL;NO_EVT
            null,                                       //STATISTIC->STAT
            DataHandlerPower.linkHandler(this),         //POWER->PWR
            DataHandlerVolumeVol.linkHandler(this),     //VOLUME->VOL
            DataHandlerVolumeVolrel.linkHandler(this),  //VOLUME->VOLREL;NO_EVT
            DataHandlerVolumeMute.linkHandler(this),    //VOLUME->MUTE
            DataHandlerVolumeBalrel.linkHandler(this),  //VOLUME->BALREL;NO_EVT
            DataHandlerVolumeBal.linkHandler(this),     //VOLUME->BAL
            DataHandlerSource.linkHandler(this),        //SOURCE->SRC
            DataHandlerSpeaker.linkHandler(this),       //SPEAKER->SPK
            null,                                       //SPEAKER->SPKCFG
            null                                        //TIME->TIME
        );
        
        
        
        
        //control=c;
        handlingThread = new Thread(()->
        {
            while(true)
            {
                 //suspected_package = ;
                int listend = MAX_PACKET_SIZE>receivebuffer.size()?receivebuffer.size():MAX_PACKET_SIZE;
                //int ls = receivebuffer.size();
                /*if(ls==0)
                {
                    continue;
                }*/
                List<Byte> suspected_package =null;
                synchronized(receivebuffer)
                {
                    suspected_package = new ArrayList<Byte>(((ArrayList<Byte>)receivebuffer.clone()).subList(0, (listend)));
                }
                if(suspected_package.size()<1||suspected_package==null)
                {
                    if(suspected_package==null)
                    {
                        l.trace("this shouldnt happen");
                    }
                    continue;
                }
                byte hdr = suspected_package.get(0);
                int size_first = ((hdr^0xff)&INVERTED_SIZE_MASK)>>>INVERTED_SIZE_OFFSET;
                int size_last = (hdr&BASE_SIZE_MASK);
                if(size_first!=size_last)
                {
                    receivebuffer.remove(0);
                    continue;
                }
                if(suspected_package.size()>size_first+1)
                {
                    int crc = CRC.CRC8_START;
                    int realsize = size_first+2;
                    for(int i=0;i<realsize;i++)
                    {
                        crc = CRC.crc8((byte)crc,suspected_package.get(i));
                    }
                    if(crc==0)//CRC mit gültigem CRC-wert soll immer auf 0 enden
                    {
                        List<Byte> packet = new ArrayList<>(MAX_PACKET_SIZE);
                        for(int i=1;i<realsize;i++)//header weg
                        {
                            packet.add(suspected_package.get(i));
                            receivebuffer.remove(0);
                        }
                        packetReceived(packet,hdr);
                    }
                    else
                    {
                        receivebuffer.remove(0);
                    }
                }
                else
                {
                    //receivebuffer.remove(0);
                }
                try
                {
                    Thread.sleep(1);
                } catch (InterruptedException ex)
                {
                    ex.printStackTrace();
                }
            }
           
        });
        handlingThread.setName("ProtocolReceiverSE540");
    }
    public void startReceive()
    {
        handlingThread.start();
    }
    
    public void receiveByte(byte b)
    {
        synchronized(receivebuffer)
        {
            receivebuffer.add(b);
            l.trace(receivebuffer.size());
        }
    }
    
    /**
     * Removes the callback for the current running cmd
     */
    private void unregister_callback()
    {
        requestResponseListener=null;
    }
    /**
     * Generates a packet for sending via the UART. CRC and frame is added by this method before send
     * @param cnt 2-bit counter that should change when sending new packet
     * @param service integer ID of the selected service
     * @param command integer ID of the command type, see SRV_* constants
     * @param userdata Userdata bytes of the packet
     * @param callback Callback handler for responses to this packet;
     * @throws InvalidPacketException
     */
    public synchronized void send_packet(int cnt, int service,int command, List<Byte> userdata, Consumer<List<Byte>> callback) throws InvalidPacketException
    {
        acked=false;
        l.trace("Packet go!");
        retries=0;
        
        List<Byte> raw_packet = new ArrayList<>(MAX_PACKET_SIZE);
        for(int i=0;i<MAX_PACKET_SIZE;i++)
        {
            raw_packet.add(i,(byte)0);
        }
        int udsize = userdata.size()+1;
        if(udsize>(1<<BASE_SIZE_LENGTH))
        {
            throw new InvalidPacketException("Userdata too long");
        }
        if(cnt>(1<<CNT_LENGTH))
        {
            throw new InvalidPacketException("Counter too large");
        }
        int udsize_inv = udsize^((1<<BASE_SIZE_LENGTH)-1);
        raw_packet.set(0, (byte)((cnt<<CNT_OFFSET)|(udsize_inv<<INVERTED_SIZE_OFFSET)|(udsize)));
        if(command>(1<<CMD_LENGTH))
        {
            throw new InvalidPacketException("Command out of range");
        }
        if(service>(1<<SRV_LENGTH))
        {
            throw new InvalidPacketException("Service out of range");
        }
        raw_packet.set(CMD_BYTE,(byte)0);
        if(CMD_BYTE!=SRV_BYTE)
        {
            raw_packet.set(SRV_BYTE,(byte)0);
        }
        raw_packet.set(CMD_BYTE,(byte)(raw_packet.get(CMD_BYTE)|(command<<CMD_OFFSET)));
        raw_packet.set(SRV_BYTE,(byte)(raw_packet.get(SRV_BYTE)|(service<<SRV_OFFSET)));
        int packetlength = USRDATA_REQ_START_BYTE+userdata.size();
        raw_packet.addAll(USRDATA_REQ_START_BYTE, userdata);
        for(int i=0;i<userdata.size();i++)
        {
            raw_packet.set(i+USRDATA_REQ_START_BYTE, userdata.get(i));
        }
        int crc = CRC.CRC8_START;
        for(int i=0;i<packetlength;i++)
        {
            crc = CRC.crc8((byte)crc,raw_packet.get(i));
        }
        raw_packet.set(packetlength,(byte)crc);
        while(raw_packet.size()>packetlength+1)
        {
            raw_packet.remove(packetlength+1);
        }
        lastCmdStartTime = System.currentTimeMillis();
        Byte[] rawpkg = raw_packet.toArray(new Byte[0]);
        DriverSe540.getDriver().sendDataViaUart(rawpkg);
        //TODO start timeout countdown
        new Thread(()->{
            try
            {
                              
                while(retries<10)
                {
                    Thread.sleep(50);
                    if(lastCmdStartTime+100>System.currentTimeMillis())
                    {
                        DriverSe540.getDriver().sendDataViaUart(rawpkg);
                        retries++;
                    }
                    else
                    {
                        if(acked)
                        {
                            return;
                        }
                    }
                }
            } catch (InterruptedException ex)
            {
                ex.printStackTrace();
            }
        });
        requestResponseListener=(packet)->
        {
            
            int statebyte = packet.get(RET_ST_BYTE);
            statebyte=(statebyte&RET_ST_MASK)>>>RET_ST_OFFSET;
            if(statebyte==RET_ST_PND)
            {
                lastCmdStartTime = System.currentTimeMillis();
                return;
            }
            acked=true;
            if(callback!=null)
            {
                callback.accept(packet);
            }
        };
//        SWTT
        //TODO consumer erstellen der auf Resend nötig prüft, dortrein dann den echten callback falls richtige antwort
    }

    /**
     * Sends a response packet
     * @param cnt Continue value of the source packet
     * @param req_crc CRC of the source packet
     * @param ret_st Statuscode of the response
     * @param userdata payload packets
     * @throws InvalidPacketException 
     */
    public void send_response(int cnt, int req_crc,int ret_st, List<Byte> userdata) throws InvalidPacketException
    {
        //if(true)
        //     return;
        // throw new UnsupportedOperationException("FIXME");
        List<Byte> raw_packet = new ArrayList<>(MAX_PACKET_SIZE);
        int udsize = userdata.size();
        if(udsize>(1<<BASE_SIZE_LENGTH))
        {
            throw new InvalidPacketException("Userdata too long");
        }
        if(cnt>(1<<CNT_LENGTH))
        {
            throw new InvalidPacketException("Counter too large");
        }
        if(ret_st>(1<<RET_ST_LENGTH))
        {
            throw new InvalidPacketException("Response-Status too large");
        }
        int udsize_inv = udsize^((1<<BASE_SIZE_LENGTH)-1);
        raw_packet.add(0, (byte)((cnt<<CNT_OFFSET)|(udsize_inv<<INVERTED_SIZE_OFFSET)|(udsize)));
        raw_packet.add(CMD_BYTE,(byte)0);
        if(RET_ST_BYTE!=SRV_BYTE)
        {
            raw_packet.add(RET_ST_BYTE,(byte)0);
        }
        raw_packet.add(RET_ST_BYTE,(byte)(raw_packet.get(RET_ST_BYTE)|(ret_st<<RET_ST_OFFSET)));
        raw_packet.add(SRV_BYTE,(byte)(raw_packet.get(SRV_BYTE)|(SRV_RET<<SRV_OFFSET)));
        int packetlength = USRDATA_RET_START_BYTE+userdata.size();
        raw_packet.addAll(USRDATA_RET_START_BYTE, userdata);
        int crc = CRC.CRC8_START;
        for(int i=0;i<packetlength;i++)
        {
            crc = CRC.crc8((byte)crc,raw_packet.get(i));
        }
        raw_packet.add(packetlength,(byte)crc);
        DriverSe540.getDriver().sendDataViaUart(raw_packet.toArray(new Byte[0]));
        
        //TODO consumer erstellen der auf Resend nötig prüft, dortrein dann den echten callback falls richtige antwort
    }
    
    
    
    //Todo trigger resend bei PacketLoss via timeout
    private void packetReceived(List<Byte> packet,int hdr)
    {
       
        int srv = packet.get(SRV_BYTE-1)&SRV_MASK>>>SRV_OFFSET;
        //int ret_ST = (packet.get(RET_ST_BYTE)&RET_ST_MASK)>>RET_ST_OFFSET;
        if(srv!=SRV_RET)
        {
            if(srv!=SRV_EVT)
            {
                return;
            }
            
            handle_event(packet,hdr);
        }
        else
        {
            if(requestResponseListener!=null)
            {
                requestResponseListener.accept(packet);
            }
        }
    }
    private void handle_event(List<Byte> packet,int hdr)
    {
        int cmd=((int)packet.get(CMD_BYTE-1));
        cmd=cmd&CMD_MASK;
        cmd=cmd>>>CMD_OFFSET;
        int cnt = (hdr&CNT_MASK)>>>CNT_OFFSET;
        int req_crc=packet.get(packet.size()-1);
        //int cnt = packet.get(CNT_BYTE)&CNT_MASK>>CNT_OFFSET;
        boolean inRange=cmd<dataHandlers.size();
        DataHandler eventhandler = dataHandlers.get(cmd);
        if(inRange&&eventhandler!=null)
        {
            int retval = eventhandler.handleEvent(packet);
            if(retval<0)
            {
                send_response(cnt,req_crc , RET_ST_ACK,new ArrayList<>());
            }
            else
            {
                send_response(cnt,req_crc , RET_ST_NACK,Arrays.asList(new Byte[]{((byte)(retval&0xFF))}));
            }
        }
        else
        {
            send_response(cnt,req_crc , RET_ST_NACK,Arrays.asList(new Byte[]{NACK_UNKNOWN}));
            l.warn("Got unknown command ID {} in event response",cmd);
        }

    }
    
    private void respond(int cnt, int crc, int stts)
    {
        List<Byte> pkg = new ArrayList<>();
        if(stts<128)
        {
            stts=RET_ST_ACK;
        }
        else
        {
            pkg.add((byte)stts);
        }
        send_response(cnt, crc, stts, pkg);
    }
    
    void sendPing()
    {
        DriverSe540.getDriver().sendDataViaUart(new Byte[]{0b00111000,CRC.crc8(CRC.CRC8_START,0b00111000)});
    }
}
