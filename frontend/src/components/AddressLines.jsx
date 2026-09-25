import { formatAddressLines } from "../utils/address";
import "./AddressLines.css";

// A postal address as a block of lines -- <address> is the semantic element for exactly this.
export default function AddressLines({ address }) {
  return (
    <address className="address-lines">
      {formatAddressLines(address).map((line, index) => (
        <span key={index}>{line}</span>
      ))}
    </address>
  );
}
