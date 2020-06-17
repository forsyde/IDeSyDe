import static org.junit.Assert.*;

import java.io.File;

import org.junit.Test;

import ForSyDe.Model.IO.ForSyDeIO;
import desyde.preprocessing.Unroller;

public class unrollTest {

	@Test
	public void test() {
		try {
			File f = new File("silly.xmi");
			ForSyDeIO fio = ForSyDeIO.parseXMI(f);
			Unroller ur = new Unroller();
			ur.unrollModel(fio);
		} catch (Exception e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}

}
