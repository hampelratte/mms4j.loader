package org.hampelratte.net.mms.messages.server.decoders;

import org.apache.mina.core.buffer.IoBuffer;
import org.apache.mina.core.session.IoSession;
import org.hampelratte.net.mms.io.RemoteException;
import org.hampelratte.net.mms.io.util.HRESULT;
import org.hampelratte.net.mms.messages.server.MMSResponse;
import org.hampelratte.net.mms.messages.server.ReportDisconnectedFunnel;

/**
 * Decoder for {@link ReportDisconnectedFunnel} objects
 *
 * @author <a href="mailto:hampelratte@users.berlios.de">hampelratte@users.berlios.de</a>
 */
public class ReportDisconnectedFunnelDecoder extends MMSResponseDecoder {

    @Override
    public MMSResponse doDecode(IoSession session, IoBuffer b) throws Exception {
        ReportDisconnectedFunnel rdf = new ReportDisconnectedFunnel();
        rdf.setHr(b.getInt());
        if(rdf.getHr() != 0) {
            throw new RemoteException(HRESULT.hrToHumanReadable(rdf.getHr()));
        }
        rdf.setPlayIncarnation(b.getInt());
        return rdf;
    }

}
