import javax.sound.midi.*;
import java.io.*;

public class CustomCleanup {
  // Note: this was a quick program I wrote to normalize the velocity of MIDI notes.
  public static void main(String[] args) throws Exception {
    
    Sequence inSeq = MidiSystem.getSequence(new File(args[0]));
    
    Track ti = inSeq.getTracks()[2];
    Track to = inSeq.createTrack();
    
    for (int i = 0; i < ti.size(); i++) {
      MidiEvent event = ti.get(i);
      MidiMessage msg = event.getMessage();
      
      if (msg instanceof ShortMessage) {
        ShortMessage shortMsg = (ShortMessage) msg;
        int cmd = shortMsg.getCommand();
        int chn = shortMsg.getChannel();
        int dt1 = shortMsg.getData1();
        
        if (cmd == ShortMessage.NOTE_ON || cmd == ShortMessage.NOTE_OFF) {
          shortMsg.setMessage(cmd, chn, dt1, 60);
        }
        MidiEvent res = new MidiEvent(shortMsg, event.getTick());
        to.add(res);
      }
      else {
        to.add(event);
      }
    }
    
    inSeq.getTracks()[2] = to;
    
    MidiSystem.write(inSeq, 1, new File("output.mid"));
  }
}
