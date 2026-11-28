const PDF_HEADER = /^(?:\xEF\xBB\xBF)?[\x00\t\n\f\r ]*%PDF-(?:1\.[0-7]|2\.0)[\r\n]/;
const PDF_EOF = /%%EOF[\x00\t\n\f\r ]*$/;

export function hasPdfHeader(bytes: Buffer): boolean {
  return PDF_HEADER.test(bytes.subarray(0, 1024).toString("latin1"));
}

export function hasPdfEndMarker(bytes: Buffer): boolean {
  return PDF_EOF.test(bytes.subarray(-1024).toString("latin1"));
}
