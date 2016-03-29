package edu.buffalo.cse.cse486586.groupmessenger2;
import android.app.Activity;
import java.io.PrintWriter;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Arrays;
import android.content.ContentValues;
import android.content.Context;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.widget.EditText;
import android.widget.TextView;
import java.io.BufferedReader;
import android.telephony.TelephonyManager;
import android.text.method.ScrollingMovementMethod;
import android.util.Log;
import android.view.Menu;
import android.view.View;
import android.view.View.OnClickListener;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.Hashtable;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * GroupMessengerActivity is the main Activity for the assignment.
 *
 * @author stevko
 *
 */

public class GroupMessengerActivity extends Activity {
    private static List<String> PORTS = new ArrayList<String>(Arrays.asList("11108", "11112", "11116", "11120", "11124"));
    static final String TAG = GroupMessengerActivity.class.getSimpleName();
    static final String BREAK_MSG_SEND = "!#!#!#";
    static final String BREAK_MSG_PROPOSAL = "!~!~!~";
    static final String BREAK_MSG_FINAL = "!@!@!@";
    static final String ACK = "!@#$!@#$";
    final Uri uri = buildUri("content", "edu.buffalo.cse.cse486586.groupmessenger2.provider");
    private final Map<String, Integer> ClientMembers = new Hashtable<String, Integer>();
    private static String myPort;
    Queue<Message> HBQueue = new ConcurrentLinkedQueue<Message>();            //queue for ISIS algo
    Queue<Message> debug_delivery_queue = new ConcurrentLinkedQueue<Message>();

    int currentMsgID;

    private static Uri buildUri(String scheme, String authority) {
        Uri.Builder uriBuilder = new Uri.Builder();
        uriBuilder.authority(authority);
        uriBuilder.scheme(scheme);
        return uriBuilder.build();
    }


    private int get_pid_from_port(String port) {
        return ClientMembers.get(port);
    }


    private void setClientMembers(){
        ClientMembers.put("11108", 0);
        ClientMembers.put("11112", 1);
        ClientMembers.put("11116", 2);
        ClientMembers.put("11120", 3);
        ClientMembers.put("11124", 4);
    }
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_group_messenger);
        currentMsgID = 0;

        /*
         * TODO: Use the TextView to display your messages. Though there is no grading component
         * on how you display the messages, if you implement it, it'll make your debugging easier.
        */
        setClientMembers();
        setPorts();

        try {

            Log.d(TAG, "initiating server task");
            ServerSocket serverSocket = new ServerSocket(10000);
            new ServerTask().executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR, serverSocket);
        } catch (IOException e) {

            Log.e(TAG, "cannot start server task");
        }
/*
         * TODO: You need to register and implement an OnClickListener for the "Send" button.
         * In your implementation you need to get the message from the input box (EditText)
         * and send it to other AVDs.
         */
        final TextView tv  = (TextView) findViewById(R.id.textView1);
        tv.setMovementMethod(new ScrollingMovementMethod());

        findViewById(R.id.button1).setOnClickListener(
                new OnPTestClickListener(tv, getContentResolver()));

        final EditText input=(EditText)findViewById(R.id.editText1);

        findViewById(R.id.button4).setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                String msg = input.getText().toString();
                input.setText(""); // This is one way to reset the input box.
                //Log.d(TAG, "message is: " + msg);
                tv.append("ME: " + msg + "\n");
                // tv.append("its "+ msg.getidNum() + "\n" );
                new ClientTask().executeOnExecutor(AsyncTask.SERIAL_EXECUTOR, msg);
            }
        });



    }
    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.activity_group_messenger, menu);
        return true;
    }

    private void setPorts() {
        TelephonyManager tel = (TelephonyManager) this.getSystemService(Context.TELEPHONY_SERVICE);
        String portStr = tel.getLine1Number().substring(tel.getLine1Number().length() - 4);
        myPort = String.valueOf((Integer.parseInt(portStr) * 2)); //short cut to set ports up
    }

    public synchronized void remove_client(String port) {
        for (String removed : PORTS){
            if (removed.equals(port)){
                PORTS.remove(removed); //helper method , goes through PORT list, removes broken port.
            }
        }
    }
    private Message get_msg_from_hold_back(int pid, int msg_id) {

        for (Message msgToFind : HBQueue) {
            int msgToFindId = msgToFind.get_message_id(); // get msg info
            int msgToFindPid = msgToFind.get_process_id();
            if ((pid == msgToFindPid) && (msg_id == msgToFindId)) {return msgToFind;} // see if its the msg we want.
            else {
                Log.e(TAG, "Error, msg not found. Missing pid: " + msgToFindPid + " msg_id " + msgToFindId);

            }
        }
        return null;
    }

    private class ServerTask extends AsyncTask<ServerSocket, String, Void> {


        public void sendMsg(String message, Socket socket) {
            String[] msg_segs = message.split(BREAK_MSG_SEND);
            String msg = msg_segs[0];
            String sendPids = msg_segs[1];
            String sendMsgId = msg_segs[2];

            int pid = get_pid_from_port(myPort);

            Log.d(TAG, "pid:" + Integer.toString(pid) + " sendMsg: " + message +
                    " sendPids " + sendPids +
                    " sendMsgId: " + sendMsgId);

            boolean isSender = sendPids.equals(Integer.toString(get_pid_from_port(myPort))); //checks if this id = the id from the stack


            int tempMsgId;         // checks if the ID of this msg is the ID that is being sent.
            if (isSender == true) {
                tempMsgId = currentMsgID;
            } else {
                tempMsgId = ++currentMsgID;
                Message newMsg = new Message(msg, 0.0, Integer.parseInt(sendMsgId), Integer.parseInt(sendPids));
                HBQueue.add(newMsg); //adds the msg to the 1st queue
            }

            String response_msg = sendMsgId + BREAK_MSG_PROPOSAL +
                    Integer.toString(tempMsgId) + BREAK_MSG_PROPOSAL +
                    Integer.toString(get_pid_from_port(myPort)) + BREAK_MSG_PROPOSAL;

            // send back the proposed sequence number
            try {
                Log.d(TAG, "pid:" + Integer.toString(pid) + " RECEIVED_SEND: " + msg + " sending back proposal: " + Integer.toString(tempMsgId) +
                        " for sendPids: " + sendPids +
                        " sendMsgId: " + sendMsgId);

                PrintWriter writer = new PrintWriter(socket.getOutputStream(), true);
                writer.println(response_msg);
                writer.flush();
            } catch (IOException e) {
                Log.e(TAG, "error getting printwriter");
            }

        }


        public void handle_message_final(String message, Socket socket) {
            String[] msg_segs = message.split(BREAK_MSG_FINAL);
            int msg_id = Integer.parseInt(msg_segs[0]);
            int pid = Integer.parseInt(msg_segs[1]);
            String final_seq_num = msg_segs[2];

            String my_pid = Integer.toString(get_pid_from_port(myPort));

            // send ACK for final message
            try {
                Log.d(TAG, "FINAL_HANDLER: sending ACK");
                PrintWriter writer = new PrintWriter(socket.getOutputStream(), true);
                writer.println(ACK);
            } catch (IOException e) {
                Log.e(TAG, "error sending ACK");
            }

            // find this message in the hold back queue
            Message final_message = get_msg_from_hold_back(pid, msg_id);
            if (final_message == null) {
                Log.e(TAG, "FINAL_HANDLER: missing pid: " + pid + " msg_id " + msg_id);
                return;
            }

            Log.d(TAG, "pid:" + my_pid + " FINAL_HANDLER: " + final_message.getMessage() +
                    " msg_id: " + msg_id +
                    " pid: " + pid +
                    " final_seq_num: " + final_seq_num);


            HBQueue.remove(final_message);
            debug_delivery_queue.add(final_message);

            ContentValues new_entry = new ContentValues();
            new_entry.put(Key_Value_Contract.UID, Double.parseDouble(final_seq_num));
            new_entry.put(Key_Value_Contract.COLUMN_VALUE, final_message.getMessage());

            Uri result = getContentResolver().insert(uri, new_entry);
            if (result == null) {
                Log.e(TAG, "error inserting value into content provider");
                return;
            } else {
                Log.d(TAG, "pid:" + my_pid + " FINAL_HANDLER: inserted " + final_message.getMessage() +
                        "with sequence: " + final_message.get_seq_num());
                publishProgress(final_message.getMessage());
            }
            Log.d(TAG, result.toString());

        }


        @Override
        protected Void doInBackground(ServerSocket... sockets) {
            ServerSocket serverSocket = sockets[0];
            try {

                while (true) {

                    Socket clientSocket = serverSocket.accept();
                    BufferedReader in = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));

                    //Log.d(TAG, "server accepted client");

                    String message;
                    while ((message = in.readLine()) != null) {

                        boolean is_send = message.contains(BREAK_MSG_SEND);
                        boolean is_final = message.contains(BREAK_MSG_FINAL);

                        if (is_send) {
                            sendMsg(message, clientSocket);
                        } else if (is_final) {
                            handle_message_final(message, clientSocket);
                        }

                        //publishProgress(message);
                    }
                }

            } catch (NullPointerException err) {
                Log.e(TAG, "client socket was not initialized properly");
            } catch (IOException err) {
                Log.e(TAG, "client socket was not initialized properly");
            }

            return null;
        }

        protected void onProgressUpdate(String... strings) {
            final TextView tv  = (TextView) findViewById(R.id.textView1);
            tv.append(strings[0] + '\n');
        }

    }

    private class ClientTask extends AsyncTask<String, Void, Void> {


        public void deliver_final_message(Message final_msg) {

            Log.d(TAG, "DELIVERING_FINAL: " + final_msg.getMessage() +
                    " sequence number: " + final_msg.get_seq_num());

            // send pid and msg_id as identifiers, and report final sequence id
            String announce_final_message =
                    Integer.toString(final_msg.get_message_id()) + BREAK_MSG_FINAL +
                            Integer.toString(final_msg.get_process_id()) + BREAK_MSG_FINAL +
                            Double.toString(final_msg.get_seq_num()) + BREAK_MSG_FINAL;

            // send out the final message
            try {
                for (String destination_port : PORTS) {
                    Log.d(TAG, "pid:" + get_pid_from_port(myPort) + " SEND_FINAL: " + final_msg.getMessage() +
                            " to " + get_pid_from_port(destination_port));
                    Socket client_socket = new Socket(InetAddress.getByAddress(new byte[]{10, 0, 2, 2}),
                            Integer.parseInt(destination_port));
                    PrintWriter writer = new PrintWriter(client_socket.getOutputStream(), true);
                    writer.println(announce_final_message);

                    writer.flush();

                    client_socket.setSoTimeout(1000);

                    BufferedReader in = new BufferedReader(new InputStreamReader(client_socket.getInputStream()));
                    String input;
                    if ((input = in.readLine()) != null) {

                        if (input.equals(ACK)) {
                            Log.d(TAG, "SEND_FINAL: " + final_msg.getMessage() +
                                    " server ACKed back");
                        }

                    } else {
                        Log.d(TAG, "SEND_FINAL: " + final_msg.getMessage() + " no ACK from server, resending...");
                        writer.println(announce_final_message);
                    }

                    writer.flush();
                    writer.close();
                    client_socket.close();
                }
            } catch (UnknownHostException e) {
                Log.e(TAG, "SEND_FINAL: unknown host error " + e.getMessage());
            } catch (IOException e) {
                Log.e(TAG, "error connecting sockets for final message");
            }

        }


        public void handle_message_proposal(String message) {

            String[] msg_segs = message.split(BREAK_MSG_PROPOSAL);
            String sendMsgId = msg_segs[0];
            String proposed_msg_id = msg_segs[1];
            int responder_pid = Integer.parseInt(msg_segs[2]);

            int my_pid = get_pid_from_port(myPort);

            Message newMsg = get_msg_from_hold_back(my_pid, Integer.parseInt(sendMsgId));
            if (newMsg == null) {
                Log.e(TAG, "sent message could not be found in hold-back_queue");
                return;
            }

            Log.d(TAG, "pid:" + Integer.toString(my_pid) + " HANDLE_PROPOSAL: " + newMsg.getMessage() +
                    " FROM " + Integer.toString(responder_pid) +
                    " proposal_sequence: " + proposed_msg_id);

            // check if proposed id is higher than current seq_num
            if (Integer.parseInt(proposed_msg_id) > newMsg.get_seq_num()) {
                newMsg.set_seq_num(Integer.parseInt(proposed_msg_id));
            }

            // set bit in delivery status for respective process response
            int delivery_status = newMsg.get_delivery_status();
            delivery_status |= 1 << responder_pid;
            newMsg.set_delivery_status(delivery_status);

            Log.d(TAG, newMsg.getMessage() + " delivery status is: " + delivery_status + " responder pid was: " + responder_pid);

            // check if all clients have responded with proposal
            if (newMsg.is_deliverable()) {

                Log.d(TAG, newMsg.getMessage() + " is deliverable");
                // prioritize sequence number by process id
                double seq_num = newMsg.get_seq_num();
                seq_num += ((double) my_pid / 10);
                newMsg.set_seq_num(seq_num);

                deliver_final_message(newMsg);
            }

        }


        @Override
        protected Void doInBackground(String... msgs) {

            // get message
            String message = msgs[0];
            String msg_id = Integer.toString(++currentMsgID);

            // add new message to queue
            Message newMsg = new Message(message,
                    Integer.parseInt(msg_id),
                    Integer.parseInt(msg_id),
                    get_pid_from_port(myPort));

            HBQueue.add(newMsg);

            List<String> clients_copy = new ArrayList<String>(PORTS);

            for (String destination_port : clients_copy) {

                String message_wrapper = message + BREAK_MSG_SEND +
                        Integer.toString(get_pid_from_port(myPort)) + BREAK_MSG_SEND +
                        msg_id + BREAK_MSG_SEND;

                Log.d(TAG, "pid:" + get_pid_from_port(myPort) + " SEND_MESSAGE: " + message +
                        " target:" + get_pid_from_port(destination_port));

                Socket client_socket;
                try {
                    client_socket = new Socket(InetAddress.getByAddress(new byte[]{10, 0, 2, 2}),
                            Integer.parseInt(destination_port));

                    PrintWriter out = new PrintWriter(client_socket.getOutputStream(), true);
                    out.println(message_wrapper);

                    out.flush();

                    // set 500ms timeout for response
                    client_socket.setSoTimeout(2200);

                    BufferedReader in = new BufferedReader(new InputStreamReader(client_socket.getInputStream()));
                    String input;
                    if ((input = in.readLine()) != null) {

                        Log.d(TAG, "just got proposal back from server");

                        out.close();
                        in.close();
                        client_socket.close();

                        handle_message_proposal(input);

                    } else {
                        Log.e(TAG, "timeout from server, send to failure");

                        failHandler(destination_port, newMsg);

                        out.close();
                        in.close();
                        client_socket.close();

                    }

                } catch (SocketException e) {
                    Log.e(TAG, "Socket exception (timeout): " + e.getMessage());
                } catch (SocketTimeoutException e) {
                    Log.e(TAG, "pid:" + Integer.toString(get_pid_from_port(myPort)) +
                            " SEND_MSG_TIMEOUT: " + message +
                            " destination: " + get_pid_from_port(destination_port));
                    failHandler(destination_port, newMsg);
                } catch (IOException e) {
                    Log.e(TAG, "io exception connecting client socket: " + e.getMessage());
                    return null;
                }

            }

            return null;

        }


        public void failHandler(String port, Message failed_msg) {

            int failing_pid = get_pid_from_port(port); // this is the failing msg and its PID.
             //lower current delivery requirement by value of failing port
           int delivery_requirement = Message.get_delivery_requirement();
            int fail_val = 1 << failing_pid;

            Message.set_delivery_requirement(delivery_requirement - fail_val);
            remove_client(port);// remove avd * it NEVER comes back* doesnt have to account for restart of process.

            Log.d(TAG, "FAILURE: " + "port: " + port +
                   " pid: " + failing_pid +
                   " new delivery requirement is: " + (delivery_requirement - fail_val));

            // check if failed message should be delivered now
            if (failed_msg.is_deliverable()) {

                Log.d(TAG, failed_msg.getMessage() + " is deliverable");
                // prioritize sequence number by process id
                double seq_num = failed_msg.get_seq_num();
                seq_num += ((double) get_pid_from_port(myPort) / 10);
                failed_msg.set_seq_num(seq_num);

                deliver_final_message(failed_msg);
            }

        }

    }



}