import {
	TriangleFanDrawMode,
	TrianglesDrawMode,
	TriangleStripDrawMode
} from '../vendor/three.module.min.js';

function toTrianglesDrawMode( geometry, drawMode ) {

	if ( drawMode === TrianglesDrawMode ) return geometry;
	if ( drawMode !== TriangleFanDrawMode && drawMode !== TriangleStripDrawMode ) return geometry;

	const index = geometry.getIndex();
	const position = geometry.getAttribute( 'position' );
	if ( position === undefined ) return geometry;

	const source = index
		? Array.from( index.array )
		: Array.from( { length: position.count }, ( _, offset ) => offset );
	const next = [];

	for ( let offset = 0; offset < source.length - 2; offset ++ ) {

		if ( drawMode === TriangleFanDrawMode ) {

			next.push( source[ 0 ], source[ offset + 1 ], source[ offset + 2 ] );

		} else if ( offset % 2 === 0 ) {

			next.push( source[ offset ], source[ offset + 1 ], source[ offset + 2 ] );

		} else {

			next.push( source[ offset + 2 ], source[ offset + 1 ], source[ offset ] );

		}

	}

	const converted = geometry.clone();
	converted.setIndex( next );
	converted.clearGroups();
	return converted;

}

export { toTrianglesDrawMode };
